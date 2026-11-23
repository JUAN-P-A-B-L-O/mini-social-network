import { useRef, useState } from 'react';
import { errorMessage, HttpError } from '../../../shared/lib/http';
import { confirmUpload, getMedia, isExpired, putImage, requestUpload } from '../services/mediaApi';
import type { MediaResponse, UploadResponse } from '../types/media';

type UploadPhase = 'IDLE' | 'REQUESTING_URL' | 'UPLOADING' | 'CONFIRMING' | 'UPLOADED' | 'ERROR';

type UploadCheckpoint =
  | { phase: 'PUT'; upload: UploadResponse; attempted: boolean }
  | { phase: 'CONFIRM' | 'READ'; id: string };

export function useImageUpload() {
  const [phase, setPhase] = useState<UploadPhase>('IDLE');
  const [media, setMedia] = useState<MediaResponse | null>(null);
  const [error, setError] = useState('');
  const checkpoint = useRef<UploadCheckpoint | null>(null);
  const running = useRef(false);
  const busy = phase === 'REQUESTING_URL' || phase === 'UPLOADING' || phase === 'CONFIRMING';

  function reset() {
    checkpoint.current = null;
    setPhase('IDLE');
    setMedia(null);
    setError('');
  }

  async function uploadImage(file: File) {
    if (running.current || phase === 'UPLOADED') return;
    running.current = true;
    setError('');

    try {
      let step = checkpoint.current;
      if (!step || (step.phase === 'PUT' && isExpired(step.upload))) {
        setPhase('REQUESTING_URL');
        step = { phase: 'PUT', upload: await requestUpload(file), attempted: false };
        checkpoint.current = step;
      }
      if (step.phase === 'PUT') {
        setPhase('UPLOADING');
        checkpoint.current = { ...step, attempted: true };
        try {
          await putImage(step.upload, file);
        } catch (failure) {
          // A retry after an interrupted response can find the original object in S3.
          const existingObject = step.attempted && failure instanceof HttpError && failure.status === 412;
          if (!existingObject) {
            if (isExpired(step.upload) || (failure instanceof HttpError && failure.status === 403)) {
              checkpoint.current = null;
            }
            throw failure;
          }
        }
        step = { phase: 'CONFIRM', id: step.upload.mediaId };
        checkpoint.current = step;
      }

      setPhase('CONFIRMING');
      if (step.phase === 'CONFIRM') {
        await confirmUpload(step.id);
        checkpoint.current = { phase: 'READ', id: step.id };
      }

      const latest = await getMedia(step.id);
      setMedia(latest);
      if (latest.status !== 'UPLOADED') {
        checkpoint.current = { phase: 'CONFIRM', id: step.id };
        throw new Error('O serviço de mídia ainda não confirmou o envio. Tente novamente.');
      }

      checkpoint.current = null;
      setPhase('UPLOADED');
    } catch (failure) {
      setError(errorMessage(failure));
      setPhase('ERROR');
    } finally {
      running.current = false;
    }
  }

  return { phase, busy, media, error, reset, uploadImage };
}
