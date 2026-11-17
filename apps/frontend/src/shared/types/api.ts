export type RepositoryProvider = 'GITHUB';

export type ReviewExecutionStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'PARTIAL'
  | 'FAILED'
  | 'CANCELLED';

export type FindingCategory =
  | 'BUG'
  | 'SECURITY'
  | 'PERFORMANCE'
  | 'MAINTAINABILITY'
  | 'TEST';

export type FindingSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type FindingOrigin = 'AI' | 'SONARQUBE' | 'CORRELATED';

export type ApprovalDecision = 'APPROVED' | 'REJECTED';

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RepositoryItem {
  id: string;
  provider: RepositoryProvider;
  externalRepositoryId: number;
  owner: string;
  name: string;
  fullName: string;
  defaultBranch: string;
  sonarProjectKey: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RegisterRepositoryPayload {
  externalRepositoryId: number;
  owner: string;
  name: string;
  defaultBranch: string;
  sonarProjectKey?: string | null;
}

export interface ReviewSummaryItem {
  id: string;
  repositoryId: string;
  repositoryFullName: string;
  pullRequestId: string;
  pullRequestNumber: number | null;
  pullRequestTitle: string | null;
  headSha: string;
  status: ReviewExecutionStatus;
  modelName: string | null;
  promptVersion: string | null;
  findingsCount: number;
  latestApprovalDecision: ApprovalDecision | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface FindingItem {
  id: string;
  fingerprint: string;
  category: FindingCategory;
  severity: FindingSeverity;
  title: string;
  description: string;
  filePath: string | null;
  startLine: number | null;
  endLine: number | null;
  evidence: string;
  recommendation: string;
  confidence: number | null;
  origin: FindingOrigin;
  externalReference: string | null;
  createdAt: string;
}

export interface ReviewDetail {
  id: string;
  repositoryId: string;
  repositoryFullName: string;
  pullRequestId: string;
  pullRequestNumber: number;
  pullRequestTitle: string;
  pullRequestHtmlUrl: string;
  authorLogin: string;
  baseRef: string;
  headRef: string;
  headSha: string;
  status: ReviewExecutionStatus;
  modelName: string | null;
  promptVersion: string | null;
  schemaVersion: string | null;
  summary: string | null;
  limitations: string[];
  inputTokens: number | null;
  outputTokens: number | null;
  failureCode: string | null;
  failureMessage: string | null;
  latestApprovalDecision: ApprovalDecision | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  findings: FindingItem[];
}

export interface DashboardSummary {
  activeRepositoriesCount: number;
  totalReviewsCount: number;
  reviewsByStatus: Record<ReviewExecutionStatus, number>;
  totalFindingsCount: number;
  findingsBySeverity: Record<FindingSeverity, number>;
  findingsByCategory: Record<FindingCategory, number>;
  approvedReviewsCount: number;
  rejectedReviewsCount: number;
}

export interface SubmitApprovalPayload {
  decision: ApprovalDecision;
  actor?: string;
  comment?: string;
}

export interface ApprovalActionResponse {
  id: string;
  reviewExecutionId: string;
  actionType: 'HUMAN_DECISION' | 'PUBLISH_REQUESTED';
  decision: ApprovalDecision;
  actor: string | null;
  comment: string | null;
  createdAt: string;
}

export interface ApiProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}

