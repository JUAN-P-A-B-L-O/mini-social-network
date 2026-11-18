# Inter Social Lab

Laboratório incremental de uma rede social baseada em microsserviços. O Lab 01.2 adiciona publicações de texto e feed paginado à fundação existente. Não há autenticação, comunicação entre serviços ou integração AWS.

## Stack e decisões

- Java 21, Micronaut Platform 4.10.24, Gradle 8.14.3 com wrapper por serviço.
- Micronaut Data JDBC, HikariCP, PostgreSQL 17, Flyway, Validation, Jackson e Management.
- JUnit 5 e Testcontainers com PostgreSQL real.
- React 19, TypeScript e Vite 8; frontend vazio, fora do Compose.

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
│       ├── app/           # Raiz React sem telas
│       └── config/        # Configuração pública para futuras APIs
├── infrastructure/postgres/init-databases.sh
├── docs/                  # Reservado para documentação técnica futura
├── compose.yaml
├── .env.example
├── .gitignore
├── AGENTS.md
└── README.md
```

`post-service` é responsável por posts e possui `posts_db`/`posts_user`. `media-service` será responsável por mídia e possui `media_db`/`media_user`. O post-service também expõe as APIs de posts e feed descritas abaixo. Cada banco possui seu próprio histórico Flyway; V1 estabelece esse histórico sem criar tabelas de negócio.

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

Os containers recebem `SERVER_PORT`, `JDBC_URL`, `DB_USER` e `DB_PASSWORD`; `DB_POOL_SIZE` é opcional e tem padrão 5. A comunicação interna usa `postgres:5432`. Somente as APIs são publicadas, no loopback do host. PostgreSQL fica acessível pela rede Compose.

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

Vite informa a URL de desenvolvimento no terminal. Não há telas ou requisições nesta etapa. `src/config/api.ts` reserva bases configuráveis por `VITE_POST_API_BASE_URL` e `VITE_MEDIA_API_BASE_URL`; os caminhos padrão `/api/posts` e `/api/media` ainda não possuem roteamento no frontend. Proxy/CORS serão definidos quando houver integração real. Variáveis `VITE_*` são públicas e nunca devem conter secrets.

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
{"id":"c33b196e-ccf0-499a-9327-4825fc931b3b","content":"Meu primeiro post no Inter Social Lab!","status":"PUBLISHED","createdAt":"2026-11-18T12:00:00Z"}
```

Conteúdo é obrigatório, não pode conter apenas espaços e tem limite de 5.000 caracteres (validação Java em unidades UTF-16). O texto é preservado sem trim. Não há autoria fictícia. `GET /api/posts/{id}` retorna o mesmo contrato com 200, 404 para UUID inexistente e 400 para identificador inválido. Erros usam o tratamento nativo do Micronaut; entradas inválidas retornam 400.

`GET /api/feed` aceita `page` a partir de zero e `size` entre 1 e 100 (padrões 0 e 20). Valores inválidos retornam 400. Exemplo de resposta vazia:

```json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

A consulta usa paginação SQL e contagem no PostgreSQL, filtrando `PUBLISHED` e ordenando por `created_at DESC, id DESC`. O índice composto `(status, created_at DESC, id DESC)` atende esse acesso. Paginação por offset é simples para esta etapa; inserções entre requisições podem deslocar páginas. Não há snapshot entre requisições.

A migração V2 cria `posts`, com UUID gerado pela aplicação, constraints de conteúdo e status e timestamp com fuso horário. A aplicação usa `Instant` UTC com precisão de microssegundos. V1 permanece intacta. `PENDING_MODERATION` e `REJECTED` reservam estados futuros; nenhum fluxo de moderação foi implementado. Código e testes seguem organização feature-first, com reuso do repository de posts pelo feed e DTOs independentes das entidades. JDBC executa no executor bloqueante, fora do event loop.

Para verificar persistência manualmente, crie um post, guarde seu UUID, execute `docker compose restart` e aguarde `docker compose up -d --wait`; consulte o mesmo UUID novamente. Preserve o volume existente.
