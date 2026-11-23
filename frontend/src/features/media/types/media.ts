export interface CreateUploadRequest {
  fileName: string;
  contentType: string;
  size: number;
}

export interface UploadResponse {
  mediaId: string;
  uploadUrl: string;
  method: 'PUT';
  requiredHeaders: Record<string, string>;
  expiresAt: string;
}

export interface MediaResponse {
  id: string;
  fileName: string;
  contentType: string;
  size: number;
  status: 'PENDING_UPLOAD' | 'UPLOADED';
  createdAt: string;
  updatedAt: string;
}
