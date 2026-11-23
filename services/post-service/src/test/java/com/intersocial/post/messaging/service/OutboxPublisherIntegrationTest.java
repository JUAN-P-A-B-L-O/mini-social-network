package com.intersocial.post.messaging.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intersocial.post.messaging.entity.OutboxEntry;
import com.intersocial.post.messaging.repository.OutboxRepository;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.transaction.TransactionOperations;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.utils.Md5Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class OutboxPublisherIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private ApplicationContext context;
    private HttpServer sqsServer;
    private ExecutorService requests;
    private SqsClient sqs;
    private OutboxPublisher publisher;
    private OutboxPublisher otherPublisher;
    private OutboxRepository outbox;
    private final ObjectMapper json = new ObjectMapper();
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failSend = new AtomicBoolean();
    private final AtomicBoolean holdFirstSend = new AtomicBoolean();
    private final CountDownLatch firstSend = new CountDownLatch(1);
    private final CountDownLatch releaseFirstSend = new CountDownLatch(1);

    @BeforeEach
    void start() throws Exception {
        context = ApplicationContext.run(Map.of(
                "datasources.default.url", postgres.getJdbcUrl(),
                "datasources.default.username", postgres.getUsername(),
                "datasources.default.password", postgres.getPassword(),
                "moderation.sqs.enabled", false));
        outbox = context.getBean(OutboxRepository.class);
        execute("DELETE FROM image_moderation_outbox");
        execute("DELETE FROM posts");

        sqsServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        requests = Executors.newVirtualThreadPerTaskExecutor();
        sqsServer.setExecutor(requests);
        sqsServer.createContext("/", exchange -> {
            try (exchange) {
                var request = json.readTree(exchange.getRequestBody());
                String payload = request.path("MessageBody").asText();
                received.add(payload);
                if (holdFirstSend.getAndSet(false)) {
                    firstSend.countDown();
                    try {
                        if (!releaseFirstSend.await(8, TimeUnit.SECONDS)) {
                            throw new IOException("Timed out waiting to release the first SQS response");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException(exception);
                    }
                }
                boolean fail = failSend.get();
                byte[] body = json.writeValueAsBytes(fail
                        ? Map.of("__type", "InternalError", "message", "Retry later")
                        : Map.of("MessageId", "sqs-message-id", "MD5OfMessageBody",
                                HexFormat.of().formatHex(Md5Utils.computeMD5Hash(payload.getBytes(StandardCharsets.UTF_8)))));
                exchange.getResponseHeaders().set("Content-Type", "application/x-amz-json-1.0");
                exchange.sendResponseHeaders(fail ? 500 : 200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        sqsServer.start();

        var endpoint = URI.create("http://127.0.0.1:" + sqsServer.getAddress().getPort());
        sqs = SqsClient.builder().endpointOverride(endpoint).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .overrideConfiguration(config -> config.retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()))
                .build();
        String queueUrl = endpoint + "/000000000000/inter-social-moderation-requests";
        var transactions = context.getBean(TransactionOperations.class);
        publisher = publisher(transactions, queueUrl);
        otherPublisher = publisher(transactions, queueUrl);
    }

    @SuppressWarnings("unchecked")
    private OutboxPublisher publisher(TransactionOperations<?> transactions, String queueUrl) {
        return new OutboxPublisher((TransactionOperations<Connection>) transactions, outbox, sqs, queueUrl);
    }

    @AfterEach
    void stop() {
        if (sqs != null) sqs.close();
        if (sqsServer != null) sqsServer.stop(0);
        if (requests != null) requests.close();
        if (context != null) context.close();
    }

    @Test
    void sendsToSqsAndMarksPublished() throws Exception {
        var entry = addEvent();
        assertTrue(publisher.publishNext());
        assertEquals(List.of(entry.payload()), received);
        assertEquals(new Delivery(true, 0, false), delivery(entry.eventId()));
        assertFalse(publisher.publishNext());
    }

    @Test
    void retriesFailedSendOnlyAfterBackoffAndReusesTheEventId() throws Exception {
        var entry = addEvent();
        failSend.set(true);
        assertTrue(publisher.publishNext());
        assertEquals(new Delivery(false, 1, true), delivery(entry.eventId()));
        assertFalse(publisher.publishNext());
        assertEquals(List.of(entry.payload()), received);

        execute("UPDATE image_moderation_outbox SET next_attempt_at = CURRENT_TIMESTAMP");
        failSend.set(false);
        assertTrue(publisher.publishNext());
        assertEquals(new Delivery(true, 1, false), delivery(entry.eventId()));
        assertEquals(List.of(entry.payload(), entry.payload()), received);
    }

    @Test
    void resendsTheSamePayloadWhenSqsAcceptedButTheMarkDidNotCommit() throws Exception {
        var entry = addEvent();
        execute("ALTER TABLE image_moderation_outbox ADD CONSTRAINT test_reject_mark CHECK (published_at IS NULL)");
        try {
            assertThrows(RuntimeException.class, publisher::publishNext);
            assertEquals(List.of(entry.payload()), received);
            assertEquals(new Delivery(false, 0, false), delivery(entry.eventId()));
        } finally {
            execute("ALTER TABLE image_moderation_outbox DROP CONSTRAINT test_reject_mark");
        }
        assertTrue(publisher.publishNext());
        assertEquals(List.of(entry.payload(), entry.payload()), received);
        assertEquals(new Delivery(true, 0, false), delivery(entry.eventId()));
    }

    @Test
    void concurrentPublishersSkipAnEventLockedByAnotherWorker() throws Exception {
        var first = addEvent();
        holdFirstSend.set(true);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = workers.submit(publisher::publishNext);
            try {
                assertTrue(firstSend.await(5, TimeUnit.SECONDS));
                assertFalse(otherPublisher.publishNext());
                var second = addEvent();
                assertTrue(otherPublisher.publishNext());
                assertEquals(new Delivery(true, 0, false), delivery(second.eventId()));
                assertEquals(List.of(first.payload(), second.payload()), received);
            } finally {
                releaseFirstSend.countDown();
            }
            assertTrue(running.get(5, TimeUnit.SECONDS));
        }
        assertEquals(new Delivery(true, 0, false), delivery(first.eventId()));
        assertFalse(otherPublisher.publishNext());
    }

    private OutboxEntry addEvent() throws Exception {
        var postId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        execute("INSERT INTO posts (id, content, status, created_at) VALUES "
                + "('%s', 'Image test', 'PENDING_MODERATION', CURRENT_TIMESTAMP)".formatted(postId));
        var payload = json.writeValueAsString(Map.of("eventId", eventId, "postId", postId));
        return outbox.save(new OutboxEntry(eventId, postId, payload, 0));
    }

    private void execute(String sql) throws Exception {
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private Delivery delivery(UUID id) throws Exception {
        try (var connection = connect(); var statement = connection.prepareStatement("""
                SELECT published_at IS NOT NULL, attempts, next_attempt_at > CURRENT_TIMESTAMP
                FROM image_moderation_outbox WHERE event_id = ?
                """)) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return new Delivery(result.getBoolean(1), result.getInt(2), result.getBoolean(3));
            }
        }
    }

    private record Delivery(boolean published, int attempts, boolean waiting) {}
}
