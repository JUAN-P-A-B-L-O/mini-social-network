// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { deferred } from '../../../shared/testing/deferred';
import { afterEach, expect, it, vi } from 'vitest';
import { App } from '../../../app/App';

const feedUrl = '/api/feed?page=0&size=20';
const createdAt = '2026-11-22T15:00:00Z';

function feedPage(page: number, content: string[], totalPages: number) {
  return Response.json({
    content: content.map((text) => ({ id: text, content: text, status: 'PUBLISHED', createdAt })),
    page, size: 20, totalPages, totalElements: content.length,
  });
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it('shows loading, handles a failed feed, and retries the same page', async () => {
  const firstLoad = deferred<Response>();
  const fetchMock = vi.fn().mockReturnValueOnce(firstLoad.promise).mockResolvedValueOnce(feedPage(0, [], 0));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);

  expect(screen.getByText('Carregando publicações…')).toBeTruthy();
  firstLoad.resolve(new Response(null, { status: 502 }));
  expect((await screen.findByRole('alert')).textContent).toContain('HTTP 502');
  fireEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
  expect(await screen.findByText('Nenhuma publicação por aqui ainda.')).toBeTruthy();
  expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([feedUrl, feedUrl]);
});

it('renders returned text and dates, paginates and refreshes the first page after publishing', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(feedPage(0, ['Primeira página'], 2))
    .mockResolvedValueOnce(feedPage(1, ['Segunda página'], 2))
    .mockResolvedValueOnce(Response.json({ id: 'new', content: 'Nova', status: 'PUBLISHED', createdAt }))
    .mockResolvedValueOnce(feedPage(0, ['Nova', 'Primeira página'], 2));
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);

  expect((await screen.findByText('Primeira página')).closest('article')?.querySelector('time')?.dateTime).toBe(createdAt);
  expect(screen.getByText('Página 1 de 2')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Próxima' }));
  expect(await screen.findByText('Segunda página')).toBeTruthy();
  expect(screen.getByText('Página 2 de 2')).toBeTruthy();

  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Nova' } });
  fireEvent.click(screen.getByRole('button', { name: 'Publicar' }));
  expect(await within(screen.getByRole('list')).findByText('Nova')).toBeTruthy();
  expect(screen.getByText('Página 1 de 2')).toBeTruthy();
  expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
    feedUrl, '/api/feed?page=1&size=20', '/api/posts', feedUrl,
  ]);
});
