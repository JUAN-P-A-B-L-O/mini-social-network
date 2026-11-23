import { apiConfig } from '../../../config/api';
import { requestJson } from '../../../shared/lib/http';
import type { PostResponse } from '../../posts/types/post';

export interface FeedResponse {
  content: PostResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function getFeed(page: number, signal: AbortSignal): Promise<FeedResponse> {
  const query = new URLSearchParams({ page: String(page), size: '20' });
  return requestJson(`${apiConfig.feedUrl}?${query}`, 'Não foi possível carregar o feed', { signal });
}
