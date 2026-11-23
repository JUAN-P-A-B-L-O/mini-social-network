export class HttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

export async function requestJson<T>(
  url: string,
  failureMessage: string,
  options?: RequestInit,
  statusMessages: Partial<Record<number, string>> = {},
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, options);
  } catch (error) {
    if (options?.signal?.aborted) throw error;
    throw new Error('Não foi possível conectar ao serviço. Verifique a conexão e tente novamente.');
  }

  if (!response.ok) {
    throw new HttpError(response.status, statusMessages[response.status] ?? `${failureMessage} (HTTP ${response.status}).`);
  }

  try {
    return await response.json() as T;
  } catch {
    throw new Error('O serviço retornou uma resposta inválida.');
  }
}

export function jsonPost(payload: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  };
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Ocorreu um erro inesperado. Tente novamente.';
}
