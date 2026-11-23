import { Feed } from '../features/feed/components/Feed';
import { useFeed } from '../features/feed/hooks/useFeed';
import { PostComposer } from '../features/posts/components/PostComposer';
import './styles.css';

export function App() {
  const feed = useFeed();

  return (
    <>
      <header className="site-header">
        <div className="header-inner">
          <span className="project-mark" aria-hidden="true"><span /></span>
          <h1>Inter Social Lab</h1>
        </div>
      </header>
      <main>
        <PostComposer onPublished={() => feed.load(0)} />
        <Feed {...feed} />
      </main>
    </>
  );
}
