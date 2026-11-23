// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { deferred } from '../../../shared/testing/deferred';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PostComposer } from './PostComposer';

const signedUrl = 'https://bucket.example.test/uploads/media-id?signature=test';
const signedHeaders = { 'Content-Type': 'image/png', 'If-None-Match': '*' };
const createdAt = '2026-11-22T15:00:00Z';
const media = {
  id: 'media-id', fileName: 'foto.png', contentType: 'image/png', size: 3,
  status: 'UPLOADED', createdAt, updatedAt: createdAt,
};

const fetchMock = vi.fn<typeof fetch>();
const createObjectURL = vi.fn(() => 'blob:preview');
const revokeObjectURL = vi.fn();

function view() {
  const onPublished = vi.fn();
  render(<PostComposer onPublished={onPublished} />);
  return {
    onPublished,
    input: screen.getByLabelText<HTMLInputElement>('Arquivo da imagem'),
    text: screen.getByRole<HTMLTextAreaElement>('textbox', { name: 'Texto da publicação' }),
  };
}

function select(input: HTMLInputElement, file: File) {
  fireEvent.change(input, { target: { files: [file] } });
}

function image() {
  return new File(['png'], media.fileName, { type: media.contentType });
}

function signature(expiresAt = new Date(Date.now() + 60_000).toISOString()) {
  return Response.json({
    mediaId: media.id, uploadUrl: signedUrl, method: 'PUT', requiredHeaders: signedHeaders, expiresAt,
  });
}

function button(name: string) {
  return screen.getByRole<HTMLButtonElement>('button', { name });
}

beforeEach(() => {
  fetchMock.mockReset();
  createObjectURL.mockClear();
  revokeObjectURL.mockClear();
  vi.stubGlobal('fetch', fetchMock);
  vi.stubGlobal('URL', Object.assign(class extends URL {}, { createObjectURL, revokeObjectURL }));
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('PostComposer', () => {
  it('renders an accessible composer and validates blank and oversized text', () => {
    const { text, input } = view();
    expect(text.placeholder).toBe('O que está acontecendo?');
    expect(input.hidden).toBe(true);
    expect(input.accept).toBe('image/jpeg,image/png');
    expect(button('Publicar').disabled).toBe(true);

    fireEvent.change(text, { target: { value: '   ' } });
    expect(button('Publicar').disabled).toBe(true);
    fireEvent.change(text, { target: { value: 'x'.repeat(5001) } });
    expect(screen.getByText('5.001 / 5.000')).toBeTruthy();
    expect(screen.getByText('O texto deve ter até 5.000 caracteres.')).toBeTruthy();
    expect(button('Publicar').disabled).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('publishes only text, refreshes the feed and clears the draft on success', async () => {
    const { text, onPublished } = view();
    fetchMock.mockResolvedValueOnce(Response.json({
      id: 'post-id', content: 'Olá', status: 'PUBLISHED', createdAt,
    }, { status: 201 }));
    fireEvent.change(text, { target: { value: 'Olá' } });
    fireEvent.click(button('Publicar'));

    expect(await screen.findByText('Publicação enviada ao feed.')).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/posts', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"content":"Olá"}',
    });
    expect(onPublished).toHaveBeenCalledTimes(1);
    expect(text.value).toBe('');
  });

  it('blocks duplicate submissions and preserves text on a failed post', async () => {
    const { text, onPublished } = view();
    const response = deferred<Response>();
    fetchMock.mockReturnValueOnce(response.promise);
    fireEvent.change(text, { target: { value: 'Rascunho' } });
    fireEvent.submit(text.form!);
    fireEvent.submit(text.form!);

    expect(screen.getByText('Publicando…')).toBeTruthy();
    expect(button('Publicar').disabled).toBe(true);
    expect(button('Anexar imagem').disabled).toBe(true);
    response.resolve(new Response(null, { status: 500 }));

    expect((await screen.findByRole('alert')).textContent).toContain('HTTP 500');
    expect(text.value).toBe('Rascunho');
    expect(onPublished).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('previews and replaces valid images and releases the preview on removal', () => {
    const { input } = view();
    const openPicker = vi.spyOn(input, 'click');
    fireEvent.click(button('Anexar imagem'));
    expect(openPicker).toHaveBeenCalledOnce();

    const png = image();
    select(input, png);
    expect(createObjectURL).toHaveBeenCalledWith(png);
    expect(screen.getByRole('img', { name: 'Prévia da imagem selecionada' }).getAttribute('src')).toBe('blob:preview');

    const jpg = new File(['jpeg'], 'foto.jpg', { type: 'image/jpeg' });
    select(input, jpg);
    expect(screen.getByText('foto.jpg')).toBeTruthy();
    expect(createObjectURL).toHaveBeenLastCalledWith(jpg);
    expect(revokeObjectURL).toHaveBeenCalledTimes(1);

    fireEvent.click(button('Remover imagem'));
    expect(screen.queryByRole('img')).toBeNull();
    expect(revokeObjectURL).toHaveBeenCalledTimes(2);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('rejects non-JPEG/PNG, empty and oversized files', () => {
    const { input } = view();
    select(input, new File(['gif'], 'foto.gif', { type: 'image/gif' }));
    expect(screen.getByRole('alert').textContent).toBe('Escolha uma imagem JPEG ou PNG.');
    select(input, new File([], 'empty.png', { type: 'image/png' }));
    expect(screen.getByRole('alert').textContent).toBe('A imagem selecionada está vazia.');
    select(input, new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' }));
    expect(screen.getByRole('alert').textContent).toBe('A imagem deve ter até 10 MiB.');
    expect(screen.queryByRole('img')).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('requests, uploads, confirms and reads an image without creating a post', async () => {
    const { text, input, onPublished } = view();
    const file = image();
    const request = deferred<Response>();
    const put = deferred<Response>();
    const confirm = deferred<Response>();
    fetchMock.mockReturnValueOnce(request.promise).mockReturnValueOnce(put.promise)
      .mockReturnValueOnce(confirm.promise).mockResolvedValueOnce(Response.json(media));
    select(input, file);
    fireEvent.change(text, { target: { value: 'Rascunho com imagem' } });
    fireEvent.submit(text.form!);
    fireEvent.submit(text.form!);

    expect(screen.getByText('Preparando o envio…')).toBeTruthy();
    expect(button('Remover imagem').disabled).toBe(true);
    expect(button('Trocar imagem').disabled).toBe(true);
    expect(button('Enviar imagem').disabled).toBe(true);
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/media/uploads', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fileName: file.name, contentType: file.type, size: file.size }),
    });

    request.resolve(signature());
    expect(await screen.findByText('Enviando imagem ao S3…')).toBeTruthy();
    expect(fetchMock).toHaveBeenNthCalledWith(2, signedUrl, {
      method: 'PUT', headers: signedHeaders, body: file, credentials: 'omit',
      redirect: 'error', referrerPolicy: 'no-referrer',
    });

    put.resolve(new Response(null, { status: 200 }));
    expect(await screen.findByText('Confirmando o envio…')).toBeTruthy();
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/media/media-id/confirm', { method: 'POST' });

    confirm.resolve(Response.json(media));
    expect(await screen.findByText('UPLOADED')).toBeTruthy();
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/media/media-id', undefined);
    expect(screen.getByText(/aguarda a integração com a publicação e a moderação/)).toBeTruthy();
    expect(button('Imagem enviada').disabled).toBe(true);
    expect(text.value).toBe('Rascunho com imagem');
    expect(onPublished).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('explains HTTP 503 and retains the file and text for a later retry', async () => {
    const { text, input } = view();
    select(input, image());
    fireEvent.change(text, { target: { value: 'Rascunho mantido' } });
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 503 }));
    fireEvent.click(button('Enviar imagem'));

    expect((await screen.findByRole('alert')).textContent).toMatch(/HTTP 503.*AWS/);
    expect(text.value).toBe('Rascunho mantido');
    expect(screen.getByRole('img')).toBeTruthy();
    expect(button('Tentar envio novamente').disabled).toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('requests a fresh URL after expiry and never sends to an expired URL', async () => {
    const { input } = view();
    select(input, image());
    fetchMock.mockResolvedValueOnce(signature('2020-01-01T00:00:00Z'));
    fireEvent.click(button('Enviar imagem'));

    expect((await screen.findByRole('alert')).textContent).toContain('expirou');
    expect(fetchMock).toHaveBeenCalledTimes(1);

    fetchMock.mockResolvedValueOnce(signature()).mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(Response.json(media)).mockResolvedValueOnce(Response.json(media));
    fireEvent.click(button('Tentar envio novamente'));
    expect(await screen.findByText('UPLOADED')).toBeTruthy();
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/media/uploads', '/api/media/uploads', signedUrl, '/api/media/media-id/confirm', '/api/media/media-id',
    ]);
  });

  it('retries confirmation without uploading the file again', async () => {
    const { input, text } = view();
    select(input, image());
    fireEvent.change(text, { target: { value: 'Rascunho' } });
    fetchMock.mockResolvedValueOnce(signature()).mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 409 }));
    fireEvent.click(button('Enviar imagem'));
    expect((await screen.findByRole('alert')).textContent).toContain('Tente confirmar novamente');

    fetchMock.mockResolvedValueOnce(Response.json(media)).mockResolvedValueOnce(Response.json(media));
    fireEvent.click(button('Tentar envio novamente'));
    expect(await screen.findByText('UPLOADED')).toBeTruthy();
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/media/uploads', signedUrl, '/api/media/media-id/confirm', '/api/media/media-id/confirm', '/api/media/media-id',
    ]);
    expect(text.value).toBe('Rascunho');
  });

  it('reports the status returned by the final read and keeps a pending image out of the feed', async () => {
    const { input, text, onPublished } = view();
    select(input, image());
    fireEvent.change(text, { target: { value: 'Rascunho pendente' } });
    fetchMock.mockResolvedValueOnce(signature()).mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(Response.json(media))
      .mockResolvedValueOnce(Response.json({ ...media, status: 'PENDING_UPLOAD' }));
    fireEvent.click(button('Enviar imagem'));

    expect(await screen.findByText('PENDING_UPLOAD')).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toContain('ainda não confirmou');
    expect(button('Tentar envio novamente').disabled).toBe(false);
    expect(text.value).toBe('Rascunho pendente');
    expect(onPublished).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('retries the status read without repeating a successful confirmation', async () => {
    const { input } = view();
    select(input, image());
    fetchMock.mockResolvedValueOnce(signature()).mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(Response.json(media)).mockResolvedValueOnce(new Response(null, { status: 500 }));
    fireEvent.click(button('Enviar imagem'));
    expect((await screen.findByRole('alert')).textContent).toContain('Não foi possível consultar o status');

    fetchMock.mockResolvedValueOnce(Response.json(media));
    fireEvent.click(button('Tentar envio novamente'));
    expect(await screen.findByText('UPLOADED')).toBeTruthy();
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/media/uploads', signedUrl, '/api/media/media-id/confirm', '/api/media/media-id', '/api/media/media-id',
    ]);
  });

  it('recovers an S3 response interrupted by a network failure using the same signed upload', async () => {
    const { input } = view();
    select(input, image());
    fetchMock.mockResolvedValueOnce(signature()).mockRejectedValueOnce(new TypeError('Failed to fetch'));
    fireEvent.click(button('Enviar imagem'));
    expect((await screen.findByRole('alert')).textContent).toContain('Não foi possível conectar ao S3');

    fetchMock.mockResolvedValueOnce(new Response(null, { status: 412 }))
      .mockResolvedValueOnce(Response.json(media)).mockResolvedValueOnce(Response.json(media));
    fireEvent.click(button('Tentar envio novamente'));
    expect(await screen.findByText('UPLOADED')).toBeTruthy();
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/media/uploads', signedUrl, signedUrl, '/api/media/media-id/confirm', '/api/media/media-id',
    ]);
  });
});
