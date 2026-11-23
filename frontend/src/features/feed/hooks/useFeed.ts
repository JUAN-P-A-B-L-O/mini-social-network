import { useEffect, useState } from 'react';
import { errorMessage } from '../../../shared/lib/http';
import { getFeed, type FeedResponse } from '../services/feedApi';

export function useFeed() {
  const [query, setQuery] = useState({ page: 0 });
  const [result, setResult] = useState<FeedResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();

    getFeed(query.page, controller.signal).then((data) => {
      if (!controller.signal.aborted) setResult(data);
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) setError(errorMessage(failure));
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });

    return () => controller.abort();
  }, [query]);

  function load(page = query.page) {
    setLoading(true);
    setError('');
    setQuery({ page });
  }

  return { result, loading, error, load };
}
