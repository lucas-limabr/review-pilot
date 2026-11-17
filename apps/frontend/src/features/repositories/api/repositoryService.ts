import { httpClient } from '../../../shared/api/httpClient';
import type {
  PagedResponse,
  RegisterRepositoryPayload,
  RepositoryItem,
} from '../../../shared/types/api';

export const repositoryService = {
  listRepositories(page = 0, size = 20, signal?: AbortSignal): Promise<PagedResponse<RepositoryItem>> {
    return httpClient.get<PagedResponse<RepositoryItem>>(
      `/repositories?page=${page}&size=${size}`,
      signal,
    );
  },

  registerRepository(payload: RegisterRepositoryPayload): Promise<RepositoryItem> {
    return httpClient.post<RepositoryItem, RegisterRepositoryPayload>('/repositories', payload);
  },
};

