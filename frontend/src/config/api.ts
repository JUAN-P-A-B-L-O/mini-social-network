export const apiConfig = {
  postBaseUrl: import.meta.env.VITE_POST_API_BASE_URL ?? '/api/posts',
  mediaBaseUrl: import.meta.env.VITE_MEDIA_API_BASE_URL ?? '/api/media',
} as const;
