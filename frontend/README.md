# Frontend do Inter Social Lab

No diretório `frontend/`, com Node.js 22, execute:

```sh
npm ci
cp .env.example .env.local
npm run dev
```

Com os microsserviços em execução, o proxy de desenvolvimento encaminha `/api/posts` e `/api/feed` ao post-service e `/api/media` ao media-service. Ajuste os alvos em `.env.local` conforme as portas expostas. Mantenha `VITE_MEDIA_UPLOAD_MAX_BYTES` igual a `MEDIA_UPLOAD_MAX_BYTES` do backend. As variáveis `VITE_*` são públicas.

Para um build servido com roteamento na mesma origem, encaminhe os três caminhos ao microsserviço correspondente. Para acessar os microsserviços diretamente de outra origem, configure `VITE_POST_API_BASE_URL` e `VITE_MEDIA_API_BASE_URL` com URLs completas terminadas em `/api/posts` e `/api/media`. O feed usa `/api/feed` no mesmo host dos posts.

## CORS

- **post-service (acesso direto)** — permitir a origem do frontend, métodos `GET` e `POST` e cabeçalho `Content-Type` no preflight dos posts em JSON.
- **media-service (acesso direto)** — permitir a origem do frontend, métodos `GET` e `POST` e cabeçalho `Content-Type` no preflight da solicitação de upload.
- **Bucket S3 (mesmo com o proxy)** — permitir a origem exata do frontend, por exemplo `http://localhost:5173`, método `PUT` e cabeçalhos `Content-Type` e `If-None-Match`. O navegador faz o preflight `OPTIONS` direto no bucket.

Para identificar o bloqueio, confira o host da requisição `OPTIONS` no painel de rede do navegador. O Vite só encaminha requisições às APIs locais. O arquivo segue direto ao S3.

## Validação

```sh
npm test
npm run build
```

Os testes simulam as respostas HTTP das APIs e do S3. A integração real do envio depende de AWS configurada no media-service e do CORS no bucket. Após a confirmação, `UPLOADED` indica o recebimento da mídia. A associação do anexo ao post e sua moderação dependem da integração futura dos serviços.
