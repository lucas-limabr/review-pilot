import { httpClient } from '../../../shared/api/httpClient';
import type { DashboardSummary } from '../../../shared/types/api';

export const dashboardService = {
  getSummary(signal?: AbortSignal): Promise<DashboardSummary> {
    return httpClient.get<DashboardSummary>('/dashboard/summary', signal);
  },
};

