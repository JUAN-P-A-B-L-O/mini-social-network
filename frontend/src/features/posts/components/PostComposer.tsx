import { useEffect, useId, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { apiConfig } from '../../../config/api';
import { errorMessage } from '../../../shared/lib/http';
import { useImageUpload } from '../../media/hooks/useImageUpload';
import { createPost, getPost, postStatusUrl } from '../services/postApi';
import { POST_MAX_LENGTH, type PostResponse } from '../types/post';

const imageTypes = ['image/jpeg', 'image/png'];
const uploadMessages: Partial<Record<ReturnType<typeof useImageUpload>['phase'], string>> = {
  REQUESTING_URL: 'Preparando o envio…',
  UPLOADING: 'Enviando imagem ao S3…',
  CONFIRMING: 'Confirmando o envio…',
};

function imageValidationError(file: File): string {
  if (!imageTypes.includes(file.type)) return 'Escolha uma imagem JPEG ou PNG.';
  if (file.size === 0) return 'A imagem selecionada está vazia.';
  if (file.size > apiConfig.mediaUploadMaxBytes) {
    const limit = new Intl.NumberFormat('pt-BR').format(apiConfig.mediaUploadMaxBytes / (1024 * 1024));
    return `A imagem deve ter até ${limit} MiB.`;
  }
  return '';
}

export function PostComposer({ onPublished }: { onPublished: () => void }) {
  const titleId = useId();
  const countId = useId();
  const contentErrorId = useId();
  const [content, setContent] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState('');
  const [attachmentError, setAttachmentError] = useState('');
  const [postError, setPostError] = useState('');
  const [published, setPublished] = useState(false);
  const [imagePost, setImagePost] = useState<PostResponse | null>(null);
  const [checkingStatus, setCheckingStatus] = useState(false);
  const [statusError, setStatusError] = useState<{ postId: string; message: string } | null>(null);
  const [publishing, setPublishing] = useState(false);
  const submission = useRef(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const upload = useImageUpload();

  useEffect(() => {
    if (!file) {
      setPreview('');
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const overLimit = content.length > POST_MAX_LENGTH;
  const busy = publishing || upload.busy;
  const mediaId = upload.phase === 'UPLOADED' ? upload.media?.id : undefined;
  const needsUpload = file !== null && !mediaId;
  const canSubmit = needsUpload || (Boolean(content.trim()) && !overLimit);
  const uploadMessage = uploadMessages[upload.phase];
  const progress = publishing ? 'Publicando…' : uploadMessage;
  const action = !needsUpload ? 'Publicar' : upload.phase === 'ERROR' ? 'Tentar envio novamente' : 'Enviar imagem';

  function selectImage(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.currentTarget.files?.[0];
    event.currentTarget.value = '';
    if (!selected || submission.current) return;

    const invalid = imageValidationError(selected);
    setAttachmentError(invalid);
    setPublished(false);
    if (invalid) return;

    upload.reset();
    setPostError('');
    setFile(selected);
  }

  function removeImage() {
    setFile(null);
    setAttachmentError('');
    upload.reset();
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit || submission.current) return;

    submission.current = true;
    setPostError('');
    setPublished(false);

    try {
      if (file && needsUpload) {
        await upload.uploadImage(file);
        return;
      }
      setPublishing(true);
      const post = await createPost(content, mediaId);
      setContent('');
      removeImage();
      if (mediaId) {
        setImagePost(post);
        setStatusError(null);
      } else {
        setPublished(true);
        onPublished();
      }
    } catch (failure) {
      setPostError(errorMessage(failure));
    } finally {
      setPublishing(false);
      submission.current = false;
    }
  }

  async function refreshStatus() {
    if (!imagePost || checkingStatus) return;
    setCheckingStatus(true);
    setStatusError(null);
    try {
      const latest = await getPost(imagePost.id);
      setImagePost((current) => current?.id === latest.id ? latest : current);
    } catch (failure) {
      setStatusError({ postId: imagePost.id, message: errorMessage(failure) });
    } finally {
      setCheckingStatus(false);
    }
  }

  return (
    <section className="panel composer" aria-labelledby={titleId}>
      <h2 id={titleId}>Nova publicação</h2>
      <form onSubmit={submit}>
        <textarea
          aria-label="Texto da publicação"
          aria-describedby={overLimit ? `${countId} ${contentErrorId}` : countId}
          aria-invalid={overLimit}
          placeholder="O que está acontecendo?"
          rows={4}
          value={content}
          disabled={publishing}
          onChange={(event) => {
            setContent(event.target.value);
            setPostError('');
            setPublished(false);
          }}
        />
        {overLimit && <p id={contentErrorId} className="message message-error">O texto deve ter até 5.000 caracteres.</p>}

        {file && (
          <div className="attachment">
            {preview && <img src={preview} alt="Prévia da imagem selecionada" />}
            <div className="attachment-details">
              <span className="attachment-name">{file.name}</span>
              <button type="button" className="text-button" onClick={removeImage} disabled={busy}>
                Remover imagem
              </button>
            </div>
          </div>
        )}

        <div className="composer-actions">
          <input type="file" accept={imageTypes.join(',')} aria-label="Arquivo da imagem" hidden
            ref={fileInput} onChange={selectImage} disabled={busy} />
          <button type="button" className="attach-button" disabled={busy} onClick={() => fileInput.current?.click()}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <rect x="3" y="4" width="18" height="16" rx="3" />
              <circle cx="8.5" cy="9" r="1.25" />
              <path d="m3.5 17 4.5-4.5 3.5 3.5 4.5-5 4.5 5" />
            </svg>
            <span>{file ? 'Trocar imagem' : 'Anexar imagem'}</span>
          </button>
          <span id={countId} className={overLimit ? 'count count-invalid' : 'count'}>
            {content.length.toLocaleString('pt-BR')} / {POST_MAX_LENGTH.toLocaleString('pt-BR')}
          </span>
          <button type="submit" className="primary-button" disabled={busy || !canSubmit}>
            {action}
          </button>
        </div>

        {needsUpload && (
          <p className="message message-muted">O texto fica no rascunho enquanto você envia a imagem.</p>
        )}
        <div role="status" aria-live="polite">
          {progress && <p className="message message-muted">{progress}</p>}
          {published && <p className="message message-success">Publicação enviada ao feed.</p>}
          {upload.media && (
            <p className={mediaId ? 'message message-success' : 'message message-muted'}>
              Status da mídia <strong>{upload.media.status}</strong>.
              {mediaId && ' Envio confirmado.'}
            </p>
          )}
        </div>
        {attachmentError && <p role="alert" className="message message-error">{attachmentError}</p>}
        {postError && <p role="alert" className="message message-error">{postError}</p>}
        {upload.error && <p role="alert" className="message message-error">{upload.error}</p>}
      </form>
      {imagePost && (
        <div>
          <div role="status" aria-live="polite">
            <p className="message message-success">
              {imagePost.status === 'PENDING_MODERATION' && 'Publicação criada e aguardando moderação. '}
              Status da publicação <strong>{imagePost.status}</strong>.
            </p>
          </div>
          <button type="button" className="text-button" onClick={refreshStatus} disabled={checkingStatus}>
            {checkingStatus ? 'Consultando status…' : 'Atualizar status'}
          </button>
          <a href={postStatusUrl(imagePost.id)} target="_blank" rel="noreferrer">Link do status</a>
          {statusError?.postId === imagePost.id && <p role="alert" className="message message-error">{statusError.message}</p>}
        </div>
      )}
    </section>
  );
}
