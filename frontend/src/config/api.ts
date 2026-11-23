const postBaseUrl = (import.meta.env.VITE_POST_API_BASE_URL || '/api/posts').replace(/\/+$/, '');
const configuredUploadLimit = Number(import.meta.env.VITE_MEDIA_UPLOAD_MAX_BYTES);

export const apiConfig = {
  postBaseUrl,
  feedUrl: postBaseUrl.replace(/\/posts$/, '/feed'),
  mediaBaseUrl: (import.meta.env.VITE_MEDIA_API_BASE_URL || '/api/media').replace(/\/+$/, ''),
  mediaUploadMaxBytes: Number.isSafeInteger(configuredUploadLimit) && configuredUploadLimit > 0
    ? configuredUploadLimit
    : 10 * 1024 * 1024,
} as const;
