import { apiConfig } from '../../../config/api';
import { jsonPost, requestJson } from '../../../shared/lib/http';
import type { PostResponse } from '../types/post';

export function createPost(content: string): Promise<PostResponse> {
  return requestJson(apiConfig.postBaseUrl, 'Não foi possível publicar', jsonPost({ content }));
}
