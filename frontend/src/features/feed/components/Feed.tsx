import type { FeedResponse } from '../services/feedApi';

const dateFormatter = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' });

interface FeedProps {
  result: FeedResponse | null;
  loading: boolean;
  error: string;
  load: (page?: number) => void;
}

export function Feed({ result, loading, error, load }: FeedProps) {
  return (
    <section aria-labelledby="feed-title" className="feed">
      <h2 id="feed-title">Publicações</h2>
      <div className="panel feed-content">
        {loading && <p className="feed-state" role="status">Carregando publicações…</p>}
        {error && (
          <div className="feed-state">
            <p role="alert" className="message-error">{error}</p>
            <button type="button" className="secondary-button" onClick={() => load()}>Tentar novamente</button>
          </div>
        )}
        {!loading && !error && result?.content.length === 0 && (
          <p className="feed-state">Nenhuma publicação por aqui ainda.</p>
        )}
        {result && result.content.length > 0 && (
          <ul className="post-list" aria-busy={loading}>
            {result.content.map((post) => {
              const timestamp = typeof post.createdAt === 'number'
                ? new Date(post.createdAt * 1000).toISOString()
                : post.createdAt;
              return (
                <li key={post.id}>
                  <article className="feed-post">
                    <time dateTime={timestamp}>{dateFormatter.format(new Date(timestamp))}</time>
                    <p>{post.content}</p>
                  </article>
                </li>
              );
            })}
          </ul>
        )}
        {result && result.totalPages > 1 && (
          <nav className="pagination" aria-label="Paginação das publicações">
            <button type="button" className="secondary-button" disabled={loading || result.page === 0}
              onClick={() => load(result.page - 1)}>Anterior</button>
            <span>Página {result.page + 1} de {result.totalPages}</span>
            <button type="button" className="secondary-button" disabled={loading || result.page + 1 >= result.totalPages}
              onClick={() => load(result.page + 1)}>Próxima</button>
          </nav>
        )}
      </div>
    </section>
  );
}
