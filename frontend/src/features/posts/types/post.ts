export const POST_MAX_LENGTH = 5000;

export interface PostResponse {
  id: string;
  content: string;
  mediaId?: string;
  status: 'PUBLISHED' | 'PENDING_MODERATION' | 'APPROVED' | 'REJECTED' | 'REVIEW_REQUIRED';
  createdAt: number | string;
}
