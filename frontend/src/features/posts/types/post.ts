export const POST_MAX_LENGTH = 5000;

export interface PostResponse {
  id: string;
  content: string;
  status: 'PUBLISHED' | 'PENDING_MODERATION' | 'REJECTED';
  createdAt: string;
}
