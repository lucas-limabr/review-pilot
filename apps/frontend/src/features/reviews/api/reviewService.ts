import { httpClient } from '../../../shared/api/httpClient';
import type {
  ApprovalActionResponse,
  PagedResponse,
  ReviewDetail,
  ReviewSummaryItem,
  SubmitApprovalPayload,
} from '../../../shared/types/api';

export const reviewService = {
  listByRepository(
    repositoryId: string,
    page = 0,
    size = 20,
    signal?: AbortSignal,
  ): Promise<PagedResponse<ReviewSummaryItem>> {
    return httpClient.get<PagedResponse<ReviewSummaryItem>>(
      `/repositories/${repositoryId}/reviews?page=${page}&size=${size}`,
      signal,
    );
  },

  getDetail(reviewExecutionId: string, signal?: AbortSignal): Promise<ReviewDetail> {
    return httpClient.get<ReviewDetail>(`/reviews/${reviewExecutionId}`, signal);
  },

  submitApproval(
    reviewExecutionId: string,
    payload: SubmitApprovalPayload,
  ): Promise<ApprovalActionResponse> {
    return httpClient.post<ApprovalActionResponse, SubmitApprovalPayload>(
      `/reviews/${reviewExecutionId}/approval`,
      payload,
    );
  },
};

