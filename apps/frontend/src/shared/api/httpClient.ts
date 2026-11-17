import type { ApiProblemDetail } from '../types/api';

const DEFAULT_API_BASE_URL = 'http://localhost:8080/api/v1';

export class ApiClientError extends Error {
  public readonly status: number;
  public readonly detail: string;
  public readonly fieldErrors?: Record<string, string>;

  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
    this.detail = message;
    this.fieldErrors = fieldErrors;
  }
}

export function resolveApiBaseUrl(configuredUrl?: string): string {
  const raw = configuredUrl ?? import.meta.env.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL;
  return String(raw).trim().replace(/\/+$/, '');
}

async function parseErrorResponse(response: Response): Promise<ApiClientError> {
  let detailMessage = `Falha na comunicação com a API (HTTP ${response.status}).`;
  let fieldErrors: Record<string, string> | undefined;

  try {
    const body = (await response.json()) as ApiProblemDetail;
    if (body.detail && body.detail.trim().length > 0) {
      detailMessage = body.detail;
    } else if (body.title && body.title.trim().length > 0) {
      detailMessage = body.title;
    }
    if (body.errors && typeof body.errors === 'object') {
      fieldErrors = body.errors;
    }
  } catch {
    // Fallback to default message when response is not JSON
  }

  return new ApiClientError(response.status, detailMessage, fieldErrors);
}

export const httpClient = {
  async get<T>(path: string, signal?: AbortSignal): Promise<T> {
    const baseUrl = resolveApiBaseUrl();
    const url = `${baseUrl}${path.startsWith('/') ? path : `/${path}`}`;
    let response: Response;
    try {
      response = await fetch(url, {
        method: 'GET',
        headers: {
          Accept: 'application/json',
        },
        signal,
      });
    } catch {
      throw new ApiClientError(
        0,
        `Não foi possível conectar ao backend em ${baseUrl}. Verifique se o serviço backend está em execução na porta 8080.`,
      );
    }

    if (!response.ok) {
      throw await parseErrorResponse(response);
    }

    return (await response.json()) as T;
  },

  async post<TResponse, TBody>(path: string, payload: TBody, signal?: AbortSignal): Promise<TResponse> {
    const baseUrl = resolveApiBaseUrl();
    const url = `${baseUrl}${path.startsWith('/') ? path : `/${path}`}`;
    let response: Response;
    try {
      response = await fetch(url, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
        signal,
      });
    } catch {
      throw new ApiClientError(
        0,
        `Não foi possível conectar ao backend em ${baseUrl}. Verifique se o serviço backend está em execução na porta 8080.`,
      );
    }

    if (!response.ok) {
      throw await parseErrorResponse(response);
    }

    return (await response.json()) as TResponse;
  },
};

