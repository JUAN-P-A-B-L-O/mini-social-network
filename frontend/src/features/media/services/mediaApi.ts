import { apiConfig } from '../../../config/api';
import { HttpError, jsonPost, requestJson } from '../../../shared/lib/http';
import type { CreateUploadRequest, MediaResponse, UploadResponse } from '../types/media';

const storageUnavailable = 'O armazenamento de imagens está indisponível (HTTP 503). Verifique a configuração da AWS ou tente novamente mais tarde.';

export function requestUpload(file: File): Promise<UploadResponse> {
  const payload: CreateUploadRequest = { fileName: file.name, contentType: file.type, size: file.size };
  return requestJson(`${apiConfig.mediaBaseUrl}/uploads`, 'Não foi possível preparar o envio', jsonPost(payload), {
    400: 'O serviço de mídia recusou a imagem. Verifique o tipo, nome e tamanho do arquivo.',
    503: storageUnavailable,
  });
}

export function confirmUpload(id: string): Promise<MediaResponse> {
  return requestJson(`${apiConfig.mediaBaseUrl}/${encodeURIComponent(id)}/confirm`,
    'Não foi possível confirmar o envio', { method: 'POST' }, {
      409: 'O serviço de mídia ainda não encontrou a imagem no S3. Tente confirmar novamente.',
      422: 'A imagem recebida pelo S3 diverge dos dados informados ao serviço de mídia.',
      503: storageUnavailable,
    });
}

export function getMedia(id: string): Promise<MediaResponse> {
  return requestJson(`${apiConfig.mediaBaseUrl}/${encodeURIComponent(id)}`, 'Não foi possível consultar o status da imagem');
}

export function isExpired(upload: UploadResponse): boolean {
  return !(Date.parse(upload.expiresAt) > Date.now());
}

export async function putImage(upload: UploadResponse, file: File): Promise<void> {
  if (upload.method !== 'PUT') throw new Error('O serviço de mídia retornou um método de envio inválido.');
  if (isExpired(upload)) throw new Error('O link de envio expirou. Tente novamente para gerar outro.');

  let response: Response;
  try {
    response = await fetch(upload.uploadUrl, {
      method: upload.method,
      headers: upload.requiredHeaders,
      body: file,
      credentials: 'omit',
      redirect: 'error',
      referrerPolicy: 'no-referrer',
    });
  } catch {
    throw new Error('Não foi possível conectar ao S3. Verifique a conexão e a configuração de CORS do bucket.');
  }

  if (response.ok) return;
  if (isExpired(upload)) throw new Error('O link de envio expirou. Tente novamente para gerar outro.');

  throw new HttpError(response.status, `O S3 recusou o envio (HTTP ${response.status}). Tente novamente.`);
}
