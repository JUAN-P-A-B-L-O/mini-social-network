import { apiConfig } from '../../../config/api';
import { jsonPost, requestJson } from '../../../shared/lib/http';
import type { PostResponse } from '../types/post';

export function createPost(content: string, mediaId?: string): Promise<PostResponse> {
  return requestJson(apiConfig.postBaseUrl, 'Não foi possível publicar', jsonPost({ content, mediaId }), {
    404: 'A imagem anexada não foi encontrada. Anexe a imagem novamente.',
    409: 'A imagem ainda não foi confirmada ou já está vinculada a outra publicação.',
    503: 'O serviço de mídia está indisponível. Tente publicar novamente.',
  });
}

export function postStatusUrl(id: string): string {
  return `${apiConfig.postBaseUrl}/${encodeURIComponent(id)}`;
}

export function getPost(id: string): Promise<PostResponse> {
  return requestJson(postStatusUrl(id), 'Não foi possível consultar o status da publicação');
}
