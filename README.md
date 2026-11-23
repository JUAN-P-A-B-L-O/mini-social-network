# Inter Social Lab

Laboratório incremental de uma rede social baseada em microsserviços. O Lab 01.2 adiciona publicações de texto e feed paginado. O Lab 01.3 adiciona upload direto de imagens para Amazon S3. O ambiente local ainda não possui autenticação ou comunicação entre serviços.

## Stack e decisões

- Java 21, Micronaut Platform 4.10.24, Gradle 8.14.3 com wrapper por serviço.
- Micronaut Data JDBC, HikariCP, PostgreSQL 17, Flyway, Validation, Jackson e Management.
- JUnit 5 e Testcontainers com PostgreSQL real.
- React 19, TypeScript e Vite 8; frontend executado fora do Compose.

Micronaut 5.2.6 é a versão geral atual, mas exige Java 25. A linha estável 4.10.24 foi escolhida para respeitar Java 21. Referências: [baseline Java](https://micronaut.io/2026/04/27/micronaut-framework-5-0-with-java-25-baseline/) e [versões publicadas](https://repo.maven.apache.org/maven2/io/micronaut/platform/micronaut-platform/maven-metadata.xml).

Os serviços usam projetos Gradle independentes, sem build agregador ou biblioteca de domínio compartilhada. O plugin nativo `application` gera distribuições JVM; não são necessários plugins de AOT ou imagem nativa. Pacotes começam em `com.intersocial.post` e `com.intersocial.media`; futuras funcionalidades terão seus próprios subpacotes. Não foram criadas camadas vazias.

## Estrutura

```text
.
├── services/
│   ├── post-service/      # Application, recursos, migrações, testes, wrapper e Dockerfile
│   └── media-service/     # Application, recursos, migrações, testes, wrapper e Dockerfile
├── frontend/
│   └── src/
│       ├── app/           # Raiz React
│       └── config/        # Configuração pública das APIs
├── infrastructure/postgres/init-databases.sh
├── docs/                  # Reservado para documentação técnica futura
├── compose.yaml
├── .env.example
├── .gitignore
├── AGENTS.md
└── README.md
```

`post-service` é responsável por posts e possui `posts_db`/`posts_user`. `media-service` é responsável pelos uploads de imagens e possui `media_db`/`media_user`. O post-service também expõe as APIs de posts e feed descritas abaixo. Cada banco possui seu próprio histórico Flyway; V1 estabelece esse histórico sem criar tabelas de negócio.

## Pré-requisitos e configuração

Para o backend completo: Docker Engine e Docker Compose com suporte a `service_healthy`. Não é necessário instalar Java, Gradle ou PostgreSQL no host. A primeira construção precisa de internet para baixar imagens, Gradle e dependências.

```bash
cp .env.example .env
```

Substitua os três valores de senha por senhas locais distintas antes de iniciar. `.env` é ignorado pelo Git. Não use as senhas de exemplo em ambientes compartilhados.

| Variável | Uso |
| --- | --- |
| `POSTGRES_PASSWORD` | Administração da instância local; não é entregue aos serviços |
| `POSTS_DB_PASSWORD` | Senha de `posts_user` |
| `MEDIA_DB_PASSWORD` | Senha de `media_user` |
| `POST_SERVICE_PORT` | Porta publicada no host, padrão 8081 |
| `MEDIA_SERVICE_PORT` | Porta publicada no host, padrão 8082 |

Os containers recebem `SERVER_PORT`, `JDBC_URL`, `DB_USER` e `DB_PASSWORD`; `DB_POOL_SIZE` é opcional e tem padrão 5. A comunicação com o banco usa `postgres:5432`. O post-service consulta os metadados de mídia em `http://media-service:8082` no Compose. Fora do Compose, usa `http://127.0.0.1:8082` por padrão e aceita `MEDIA_SERVICE_URL`. Somente as APIs são publicadas, no loopback do host. PostgreSQL fica acessível pela rede Compose.

## Executar o backend

```bash
docker compose up --build -d
docker compose ps
curl -f http://localhost:8081/health
curl -f http://localhost:8082/health
docker compose logs -f
```

O PostgreSQL cria os dois bancos e usuários no primeiro uso do volume. O Compose aguarda a saúde do banco antes de iniciar os serviços; cada aplicação executa suas migrações automaticamente. `/health` retorna HTTP 200 e `{"status":"UP"}` quando saudável, incluindo a verificação JDBC e sem divulgar detalhes internos.

Se uma porta já estiver ocupada, ajuste `POST_SERVICE_PORT` ou `MEDIA_SERVICE_PORT` em `.env` e use a porta correspondente no `curl`. A porta interna de cada serviço permanece 8081/8082.

```bash
docker compose restart
docker compose up -d --wait
docker compose down
```

`down` remove os containers e a rede, preservando o volume `postgres_data`. Ao executar `up` novamente, os dados e históricos de migração são reutilizados. Não use `down -v` para um encerramento normal: ele apaga os dados. Alterar senhas em `.env` não altera usuários de um volume já inicializado; nesse caso, as credenciais precisam ser alteradas de forma coordenada no banco.

## Executar um serviço individualmente

Com Docker, sem precisar de Java no host (o PostgreSQL é iniciado automaticamente):

```bash
docker compose up --build -d post-service
# Ou:
docker compose up --build -d media-service
```

Para desenvolvimento diretamente no host, instale JDK 21 e disponibilize um PostgreSQL acessível ao host com o banco e usuário correspondentes. Configure variáveis no terminal; `gradlew` não carrega `.env` automaticamente:

```bash
export JDBC_URL=jdbc:postgresql://localhost:5432/posts_db
export DB_USER=posts_user
read -rs -p 'DB password: ' DB_PASSWORD; echo
export DB_PASSWORD
cd services/post-service
./gradlew run
```

Para mídia, use `media_db`, `media_user` e o wrapper de `services/media-service`. `localhost` neste exemplo é uma execução no host, não a comunicação entre containers.

## Testes e compilação

Os testes individuais exigem JDK 21, Docker acessível ao usuário e internet no primeiro uso. Testcontainers cria bancos descartáveis independentes do Compose e das credenciais de `.env`. Os testes falham se Docker não estiver disponível; não são silenciosamente ignorados.

```bash
cd services/post-service
./gradlew test
./gradlew installDist
cd ../media-service
./gradlew test
./gradlew installDist
```

As suítes verificam inicialização, PostgreSQL, migrações e saúde. O post-service também testa criação e persistência, consulta, validação, 404, paginação, ordenação com desempate e exclusão de posts não publicados. Relatórios ficam em `build/reports/tests/test/index.html` de cada serviço. Os Dockerfiles compilam a distribuição; os testes com Testcontainers são executados separadamente, pois exigem acesso ao Docker e não fazem parte do build de imagem.

## Frontend

Requer Node.js 22.12+ (arquivo `.nvmrc`: Node 22) e npm:

```bash
cd frontend
npm ci
npm run dev
# Compilação:
npm run build
```

Abra a URL informada pelo Vite no terminal. O proxy encaminha `/api/posts` e `/api/feed` ao `post-service` em `127.0.0.1:8081` e `/api/media` ao `media-service` em `127.0.0.1:8082`. O navegador usa caminhos relativos à origem do Vite, inclusive quando ele sobe em outra porta.

Se você alterar `POST_SERVICE_PORT` ou `MEDIA_SERVICE_PORT` no Compose, consulte as portas publicadas com `docker compose port post-service 8081` e `docker compose port media-service 8082` na raiz do projeto. Copie `frontend/.env.example` para `frontend/.env.local`, ajuste `POST_API_PROXY_TARGET` e `MEDIA_API_PROXY_TARGET` para os endereços publicados e reinicie o Vite. Com as portas padrão, o arquivo local é opcional. Mantenha `VITE_POST_API_BASE_URL` e `VITE_MEDIA_API_BASE_URL` relativos para usar o proxy. Variáveis `VITE_*` são públicas e devem conter apenas valores não sensíveis.

## Validação desta fundação

Foram executados os builds independentes e os testes JUnit/Testcontainers dos dois serviços, além do build frontend em Node 22. O ambiente Compose foi validado com usuários autenticados, migrações e health checks; os comandos e resultados da entrega devem ser consultados junto às limitações do ambiente local.

Pendência de dependência frontend: `npm audit` reporta cinco ocorrências de severidade alta derivadas do advisory [GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf), em `picomatch` e sua cadeia de dependências de desenvolvimento, inclusive Vite. Na validação, o registry disponibilizava `picomatch` 4.0.7 como versão mais recente e o audit não oferecia correção compatível com Vite 8. O build passa, mas a auditoria retorna código 1. Revisar quando uma versão corrigida estiver disponível; o frontend não é iniciado pelo Compose.

## Posts e feed — Lab 01.2

Use a porta publicada pelo Compose, respeitando o `.env` local:

```bash
POST_URL="http://$(docker compose port post-service 8081)"
curl -i -X POST "$POST_URL/api/posts" \
  -H 'Content-Type: application/json' \
  -d '{"content":"Meu primeiro post no Inter Social Lab!"}'
curl "$POST_URL/api/posts/<uuid-retornado>"
curl "$POST_URL/api/feed?page=0&size=20"
```

`POST /api/posts` retorna 201, cabeçalho `Location` e corpo:

```json
{"id":"c33b196e-ccf0-499a-9327-4825fc931b3b","content":"Meu primeiro post no Inter Social Lab!","status":"PUBLISHED","createdAt":1795003200.000000000}
```

`createdAt` é expresso em segundos Unix, com fração para a precisão de microssegundos. Conteúdo é obrigatório, não pode conter apenas espaços e tem limite de 5.000 caracteres (validação Java em unidades UTF-16). O texto é preservado sem trim. Não há autoria fictícia. `GET /api/posts/{id}` retorna o mesmo contrato com 200, 404 para UUID inexistente e 400 para identificador inválido. Erros usam o tratamento nativo do Micronaut; entradas inválidas retornam 400.

`GET /api/feed` aceita `page` a partir de zero e `size` entre 1 e 100 (padrões 0 e 20). Valores inválidos retornam 400. Exemplo de resposta vazia:

```json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

A consulta usa paginação SQL e contagem no PostgreSQL, filtrando `PUBLISHED` e `APPROVED` e ordenando por `created_at DESC, id DESC`. Um índice parcial sobre esses estados atende a consulta. Paginação por offset é simples para esta etapa; inserções entre requisições podem deslocar páginas. Não há snapshot entre requisições.

A migração V2 cria `posts`, com UUID gerado pela aplicação, constraints de conteúdo e status e timestamp com fuso horário. A aplicação usa `Instant` UTC com precisão de microssegundos. A migração V3 evolui o catálogo de estados e adiciona a associação opcional à mídia e a Outbox. V1 e V2 permanecem intactas. Código e testes seguem organização feature-first, com reuso do repository de posts pelo feed e DTOs independentes das entidades. JDBC executa no executor bloqueante, fora do event loop.

Para verificar persistência manualmente, crie um post, guarde seu UUID, execute `docker compose restart` e aguarde `docker compose up -d --wait`; consulte o mesmo UUID novamente. Preserve o volume existente.

## Imagens — Lab 01.3

O cliente pede uma autorização ao `media-service` e envia o arquivo diretamente ao S3. O serviço armazena os metadados em `media_db`. A migração V2 cria a tabela `media` e preserva o histórico anterior. O serviço escuta no loopback quando executado diretamente no host. No Compose, a porta também é publicada somente no loopback do host. Mantenha essas restrições enquanto a API estiver sem autenticação.

| Endpoint | Resposta |
| --- | --- |
| `POST /api/media/uploads` | `201` com `Location` e autorização PUT. `400` para metadados inválidos ou tamanho acima do limite. |
| `POST /api/media/{id}/confirm` | `200` com metadados e status `UPLOADED`. `404` para mídia desconhecida, `409` quando o S3 informa que o objeto não existe e `422` para divergência no tamanho ou no Content-Type. A repetição após sucesso devolve os mesmos metadados. |
| `GET /api/media/{id}` | `200` com metadados ou `404` para mídia desconhecida. A resposta não inclui URL ou localização interna do objeto. |

A solicitação aceita `image/jpeg` e `image/png`, um nome original de até 255 caracteres sem caracteres de controle e um tamanho positivo em bytes. A chave é gerada como `uploads/{uuid}`, independentemente do nome original. Operações de storage retornam `503` quando a configuração ou o acesso ao S3 está indisponível. A consulta de metadados e o health check continuam disponíveis.

Uma solicitação de 1 MiB tem este formato.

```json
{"fileName":"foto.jpg","contentType":"image/jpeg","size":1048576}
```

A resposta inclui a URL e os cabeçalhos obrigatórios. A URL abaixo é ilustrativa.

```json
{
  "mediaId": "1e2a6f16-198c-492f-abc8-b10651bb3621",
  "uploadUrl": "https://bucket-exemplo.s3.us-east-1.amazonaws.com/uploads/1e2a6f16-198c-492f-abc8-b10651bb3621?assinatura=...",
  "method": "PUT",
  "requiredHeaders": {"Content-Type": "image/jpeg", "If-None-Match": "*"},
  "expiresAt": "2026-11-22T21:00:00Z"
}
```

Após a confirmação, a consulta devolve os metadados com status `UPLOADED`.

```json
{
  "id": "1e2a6f16-198c-492f-abc8-b10651bb3621",
  "fileName": "foto.jpg",
  "contentType": "image/jpeg",
  "size": 1048576,
  "status": "UPLOADED",
  "createdAt": "2026-11-22T20:55:00Z",
  "updatedAt": "2026-11-22T20:56:10Z"
}
```

### Configuração local do S3

Use um bucket S3 de uso geral, dedicado ao laboratório, na região configurada. Na AWS, habilite todas as opções de **Block Public Access**, mantenha **Object Ownership** em **Bucket owner enforced** e use criptografia padrão SSE-S3. A aplicação usa apenas operações sobre objetos. O provisionamento e a configuração do bucket ficam a cargo de um administrador autorizado.

| Variável | Uso |
| --- | --- |
| `AWS_REGION` | Região do bucket, por exemplo `us-east-1`. |
| `MEDIA_S3_BUCKET` | Nome do bucket privado. |
| `MEDIA_UPLOAD_MAX_BYTES` | Limite para o tamanho declarado, por padrão 10 MiB. |
| `AWS_CONFIG_FILE` | No WSL, caminho absoluto do arquivo de configuração. O padrão é `~/.aws/config`. |
| `AWS_SHARED_CREDENTIALS_FILE` | Caminho absoluto opcional de um arquivo de credenciais compartilhadas. O perfil `inter-social-lab` usa `aws login` e dispensa esse arquivo. |
| `AWS_LOGIN_CACHE_DIRECTORY` | No WSL, diretório do cache de `aws login`. O padrão é `~/.aws/login/cache`. |
| `MEDIA_AWS_UID`, `MEDIA_AWS_GID` | UID e GID do usuário não-root do WSL que tem acesso ao perfil e ao cache. |

Permita `s3:PutObject` e `s3:GetObject` no ARN `arn:aws:s3:::NOME_DO_BUCKET/uploads/*`. A segunda ação é exigida para `HeadObject`. Sem `s3:ListBucket`, o S3 pode devolver `403` ao consultar um objeto ausente, e a API responde `503` porque não consegue distinguir ausência de acesso negado. Para obter `409` nesse caso, conceda também `s3:ListBucket` no ARN do bucket dedicado, `arn:aws:s3:::NOME_DO_BUCKET`. [Permissões e códigos do HeadObject](https://docs.aws.amazon.com/AmazonS3/latest/API/API_HeadObject.html)

No WSL, autentique o perfil com `aws login --profile inter-social-lab` quando a sessão precisar ser renovada. Execute o Compose com o mesmo usuário não-root. O `DefaultCredentialsProvider` lê a propriedade `login_session` do perfil `inter-social-lab` e carrega as credenciais temporárias por meio do módulo `signin`. A sobreposição do Compose monta os arquivos de configuração em modo somente leitura e o diretório do cache em modo leitura e escrita para permitir a atualização da sessão pelo SDK. Os arquivos mantêm as permissões do WSL e os volumes ficam restritos aos serviços que integram com AWS. [Cadeia padrão do SDK](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html) · [Cache e renovação do aws login](https://docs.aws.amazon.com/sdkref/latest/guide/feature-login-credentials.html)

```bash
export COMPOSE_FILE=compose.yaml:compose.aws-login.yaml
export MEDIA_AWS_UID="$(id -u)" MEDIA_AWS_GID="$(id -g)"
export AWS_REGION=us-east-1
export MEDIA_S3_BUCKET=seu-bucket-privado
docker compose up --build -d
docker compose ps
```

Para repetir a configuração em novos terminais, é possível definir essas cinco variáveis no `.env` local, usando os valores numéricos de `id -u` e `id -g`. Se o arquivo de configuração ou o diretório do cache estiver em outro local, indique o caminho absoluto nas variáveis da tabela. O cache deve pertencer ao usuário do WSL, com permissão de leitura dos arquivos e escrita no diretório. Para usar o Compose sem AWS, mantenha `COMPOSE_FILE` sem a sobreposição. Para execução direta com `./gradlew run`, use `AWS_PROFILE=inter-social-lab`, as variáveis de região e bucket e a configuração JDBC descrita acima.

### Validação manual

Com bucket e credenciais configurados, execute da raiz do repositório. O exemplo usa Bash, `curl` e `jq`.

```bash
set +x
MEDIA_URL="http://$(docker compose port media-service 8082)"
IMAGE=foto.jpg
CONTENT_TYPE=image/jpeg

payload=$(jq -n --arg name "$(basename "$IMAGE")" --arg type "$CONTENT_TYPE" \
  --argjson size "$(wc -c < "$IMAGE")" '{fileName:$name,contentType:$type,size:$size}')
upload=$(curl -fsS -X POST "$MEDIA_URL/api/media/uploads" \
  -H 'Content-Type: application/json' -d "$payload")
MEDIA_ID=$(jq -er '.mediaId' <<< "$upload")
UPLOAD_URL=$(jq -er '.uploadUrl' <<< "$upload")

printf 'url = "%s"\n' "$UPLOAD_URL" | curl -fsS --config - \
  -X PUT -H "Content-Type: $CONTENT_TYPE" -H 'If-None-Match: *' --upload-file "$IMAGE"
unset UPLOAD_URL upload
curl -fsS -X POST "$MEDIA_URL/api/media/$MEDIA_ID/confirm" | jq -e 'select(.status == "UPLOADED")'
curl -fsS "$MEDIA_URL/api/media/$MEDIA_ID" | jq -e 'select(.status == "UPLOADED")'
aws s3api head-object --profile inter-social-lab --region "$AWS_REGION" \
  --bucket "$MEDIA_S3_BUCKET" --key "uploads/$MEDIA_ID" --query '[ContentLength, ContentType]'

# Opcional: verificar o registro diretamente no PostgreSQL local.
docker compose exec -T postgres psql -U postgres -d media_db -v media_id="$MEDIA_ID" <<'SQL'
SELECT id, file_name, content_type, size, status, created_at, updated_at
FROM media WHERE id = :'media_id'::uuid;
SQL
```

Os testes automatizados usam PostgreSQL via Testcontainers, um stub de storage e assinatura SDK com credenciais fictícias. A suíte roda localmente sem bucket ou credenciais reais.

```bash
(cd services/media-service && ./gradlew test installDist)
(cd services/post-service && ./gradlew test installDist)
docker compose config --quiet
docker compose up --build -d --wait
```

A URL autoriza apenas o PUT para a chave assinada por até cinco minutos e deve ser tratada como segredo temporário. O cabeçalho assinado `If-None-Match: *` impede substituir um objeto existente com a mesma autorização. Se o objeto já existir e for recusado na confirmação, solicite uma nova autorização para outra chave. A PUT não garante sozinha o limite de tamanho. Na confirmação, `HeadObject` verifica a existência e compara o tamanho real e o Content-Type registrado no S3. O nome, a extensão e o Content-Type podem ser declarados pelo cliente e não comprovam os bytes da imagem. Arquivos recusados ou abandonados podem permanecer no bucket e consumir armazenamento. A limpeza e a inspeção do conteúdo ficam para etapas futuras.

A confirmação manual é intermediária e pode ser substituída por notificações S3/SQS em uma próxima etapa. `UPLOADED` registra a confirmação do upload e permite solicitar moderação ao publicar. [Escritas condicionais no S3](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html)

## Posts com imagem — Lab 01.4.1

No frontend, escreva o texto, anexe uma imagem JPEG ou PNG e clique em **Enviar imagem**. O cliente usa a autorização PUT do S3 e confirma o upload com o media-service. Depois de ver `UPLOADED`, clique em **Publicar**. O frontend mostra o status retornado pelo post-service. Use **Atualizar status** para consultar uma vez por clique e **Link do status** para voltar à consulta depois.

`POST /api/posts` aceita `mediaId` opcional no formato UUID canônico. A consulta HTTP ao media-service verifica o identificador e o estado `UPLOADED` antes da transação do post-service. Para posts com imagem, o HTTP 201 confirma a gravação do post e da solicitação, inclui `mediaId` e retorna `PENDING_MODERATION`. O `Location` aponta para `GET /api/posts/{id}`, que retorna o estado atual. Posts de texto continuam com `PUBLISHED` e o campo `mediaId` é omitido.

| HTTP | Motivo |
| --- | --- |
| 400 | UUID da mídia ou conteúdo inválido |
| 404 | Mídia inexistente |
| 409 | Mídia ainda sem confirmação ou já vinculada a outro post |
| 503 | Falha ao consultar o media-service |

| Estado | Visibilidade no feed |
| --- | --- |
| `PUBLISHED` | Visível. Estado inicial de posts de texto. |
| `PENDING_MODERATION` | Oculto. Estado inicial de posts com imagem. |
| `APPROVED` | Visível quando houver um fluxo futuro de aprovação. |
| `REJECTED` | Oculto. Reservado para o fluxo de moderação. |
| `REVIEW_REQUIRED` | Oculto. Reservado para o fluxo de moderação. |

Os posts com imagem permanecem pendentes nesta etapa. O Lab ainda não tem Rekognition, consumidor de resultados ou rota de leitura da imagem. O bucket permanece privado e o post-service guarda somente o UUID da mídia. As APIs seguem locais enquanto estiverem sem autenticação.

### Transactional Outbox

A migração V3 adiciona a restrição de unicidade de `media_id` e a tabela `image_moderation_outbox` no PostgreSQL do post-service. O vínculo com uma imagem e o evento são gravados na mesma transação. Uma falha na Outbox desfaz a criação do post. A unicidade também protege contra requisições concorrentes.

O publisher é desabilitado por padrão. Quando habilitado, verifica a cada cinco segundos se há eventos disponíveis e processa até dez por rodada. Cada envio usa uma transação própria e reserva uma linha com `FOR UPDATE SKIP LOCKED`, permitindo que outros processos trabalhem em eventos diferentes. O SDK tem timeout de cinco segundos por chamada. O registro recebe `published_at` depois que o SQS aceita a mensagem. Na falha de envio, o registro permanece pendente, acumula uma tentativa e é reagendado com backoff exponencial de cinco segundos até quinze minutos. Eventos ficam armazenados enquanto o publisher está desabilitado.

Uma falha entre o aceite do SQS e a confirmação de `published_at` pode causar reenvio com o mesmo `eventId`. A fila Standard pode entregar duplicatas e alterar a ordem. A DLQ recebe mensagens com falhas de processamento do futuro consumidor. Falhas no envio são tratadas na própria Outbox. Os registros publicados permanecem no banco durante o laboratório.

### Evento ImageModerationRequested

```json
{
  "eventId": "042d25cd-7fb4-40dd-aaf2-6183683565bb",
  "eventType": "ImageModerationRequested",
  "eventVersion": 1,
  "occurredAt": "2026-11-22T20:00:00Z",
  "postId": "c33b196e-ccf0-499a-9327-4825fc931b3b",
  "mediaId": "1e2a6f16-198c-492f-abc8-b10651bb3621"
}
```

O corpo da mensagem é JSON. `occurredAt` é o instante UTC de criação do post, em ISO 8601. O consumidor futuro deverá interpretar `eventType` e `eventVersion`, tratar `eventId` como chave idempotente e usar `postId` e `mediaId` para localizar o post e a mídia. Bytes, localização S3, URLs assinadas e credenciais ficam fora do contrato. O envio ao SQS confirma somente a entrega da solicitação.

### Configuração do SQS

| Variável | Uso |
| --- | --- |
| `MODERATION_SQS_ENABLED` | Defina como `true` para iniciar o publisher. Padrão `false`. |
| `AWS_REGION` | Região das filas. No Compose atual, também é usada para o bucket S3. |
| `MODERATION_QUEUE_URL` | URL da fila Standard `inter-social-moderation-requests`. |
| `MODERATION_DLQ_URL` | URL de `inter-social-moderation-requests-dlq`, usada nos comandos operacionais. |

O post-service usa o AWS SDK for Java 2.x com `DefaultCredentialsProvider`. Para o perfil `inter-social-lab`, use a sobreposição `compose.aws-login.yaml` e as mesmas opções de UID, GID e cache da configuração do S3 acima. O post-service também aceita `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e `AWS_SESSION_TOKEN` de uma sessão temporária exportada no terminal. Execute `docker compose up --build -d` após configurar as variáveis. Nenhuma credencial é adicionada às imagens.

Se as duas filas ainda não existirem, um administrador autorizado pode criá-las com AWS CLI e `jq`. Use a mesma conta e região para ambas. Para filas existentes, consulte os atributos e reaproveite suas URLs. [DLQs do Amazon SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)

```bash
export AWS_PROFILE=inter-social-lab
export AWS_REGION=us-east-1

MODERATION_DLQ_URL=$(aws sqs create-queue --queue-name inter-social-moderation-requests-dlq \
  --attributes SqsManagedSseEnabled=true,MessageRetentionPeriod=1209600 --query QueueUrl --output text)
MODERATION_QUEUE_URL=$(aws sqs create-queue --queue-name inter-social-moderation-requests \
  --attributes SqsManagedSseEnabled=true,MessageRetentionPeriod=345600 --query QueueUrl --output text)

DLQ_ARN=$(aws sqs get-queue-attributes --queue-url "$MODERATION_DLQ_URL" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)
REQUEST_ARN=$(aws sqs get-queue-attributes --queue-url "$MODERATION_QUEUE_URL" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)
aws sqs set-queue-attributes --queue-url "$MODERATION_DLQ_URL" --attributes "$(
  jq -cn --arg arn "$REQUEST_ARN" \
    '{RedriveAllowPolicy:({redrivePermission:"byQueue",sourceQueueArns:[$arn]} | tojson)}'
)"
aws sqs set-queue-attributes --queue-url "$MODERATION_QUEUE_URL" --attributes "$(
  jq -cn --arg arn "$DLQ_ARN" \
    '{RedrivePolicy:({deadLetterTargetArn:$arn,maxReceiveCount:5} | tojson)}'
)"
```

- **Publisher** precisa apenas de `sqs:SendMessage` no ARN `arn:aws:sqs:REGIÃO:CONTA:inter-social-moderation-requests` com a criptografia gerenciada pelo SQS acima. A fila é informada por URL.
- **Provisionamento manual** exige `sqs:CreateQueue`, `sqs:GetQueueAttributes` e `sqs:SetQueueAttributes` nos ARNs das duas filas para os comandos acima. Para obter a URL de uma fila existente com `aws sqs get-queue-url`, use `sqs:GetQueueUrl` no ARN dessa fila.
- **Validação manual** exige `sqs:ReceiveMessage` na fila de solicitação para ler o corpo do evento. [Ações do Amazon SQS e recursos IAM](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-api-permissions-reference.html)

### Validação

Sem AWS, mantenha `MODERATION_SQS_ENABLED=false` e execute os comandos abaixo. Os testes do post-service usam PostgreSQL real e servidores HTTP locais para simular media-service e SQS. O frontend simula os serviços e o S3.

```bash
(cd services/post-service && ./gradlew test installDist)
(cd services/media-service && ./gradlew test installDist)
(cd frontend && npm ci && npm test && npm run build)
docker compose config --quiet
docker compose up --build -d --wait
```

Com S3 e SQS configurados, faça o upload e a confirmação da seção anterior para obter `MEDIA_ID`. Configure as URLs das filas no ambiente do terminal e no `.env` local, habilite o publisher e recrie o post-service com Compose. Em seguida, crie um post de teste e guarde o `id` retornado.

```bash
POST_URL="http://$(docker compose port post-service 8081)"
post=$(curl -fsS -X POST "$POST_URL/api/posts" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg id "$MEDIA_ID" '{content:"Teste de moderação",mediaId:$id}')")
POST_ID=$(jq -er 'select(.status == "PENDING_MODERATION") | .id' <<< "$post")
curl -fsS "$POST_URL/api/posts/$POST_ID"
curl -fsS "$POST_URL/api/feed?page=0&size=20"

aws sqs receive-message --queue-url "$MODERATION_QUEUE_URL" \
  --wait-time-seconds 20 --max-number-of-messages 10 --output json | \
  jq --arg id "$POST_ID" '.Messages[]?.Body | fromjson | select(.postId == $id)'
```

Faça a leitura em uma fila de laboratório. Cada recebimento conta para a política da DLQ. O evento deve conter o mesmo `postId` e `mediaId`. No banco local, confira a confirmação de envio na Outbox. O post permanece com `PENDING_MODERATION` mesmo após a publicação do evento.

```bash
docker compose exec -T postgres psql -U postgres -d posts_db -v post_id="$POST_ID" <<'SQL'
SELECT event_id, attempts, next_attempt_at, published_at
FROM image_moderation_outbox WHERE post_id = :'post_id'::uuid;
SQL
```
