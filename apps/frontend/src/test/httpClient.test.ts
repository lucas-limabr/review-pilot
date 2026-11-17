import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiClientError, httpClient, resolveApiBaseUrl } from '../shared/api/httpClient';

describe('httpClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('normalizes trailing slashes in resolveApiBaseUrl', () => {
    expect(resolveApiBaseUrl('http://localhost:8080/api/v1///')).toBe(
      'http://localhost:8080/api/v1',
    );
  });

  it('performs GET requests and returns typed JSON response', async () => {
    const mockFetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ status: 'UP' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const data = await httpClient.get<{ status: string }>('/health');
    expect(data.status).toBe('UP');
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/health$/),
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('throws ApiClientError with Problem Details message on non-2xx responses', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Resource Conflict',
          status: 409,
          detail: 'Repository auto-elite is already registered.',
        }),
        {
          status: 409,
          headers: { 'Content-Type': 'application/problem+json' },
        },
      ),
    );

    await expect(httpClient.post('/repositories', {})).rejects.toThrowError(
      new ApiClientError(409, 'Repository auto-elite is already registered.'),
    );
  });
});

