import { useCallback, useEffect, useState } from 'react';
import { dashboardService } from './features/dashboard/api/dashboardService';
import { DashboardSummarySection } from './features/dashboard/components/DashboardSummarySection';
import { repositoryService } from './features/repositories/api/repositoryService';
import { RepositoryListSection } from './features/repositories/components/RepositoryListSection';
import { reviewService } from './features/reviews/api/reviewService';
import { RepositoryReviewsSection } from './features/reviews/components/RepositoryReviewsSection';
import { ReviewDetailSection } from './features/reviews/components/ReviewDetailSection';
import { ApiClientError } from './shared/api/httpClient';
import { AppLayout } from './shared/components/AppLayout';
import type {
  DashboardSummary,
  RepositoryItem,
  ReviewDetail,
  ReviewSummaryItem,
} from './shared/types/api';

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiClientError) {
    return err.detail;
  }
  if (err instanceof Error && err.message) {
    return err.message;
  }
  return fallback;
}

export function App() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState<boolean>(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  const [repositories, setRepositories] = useState<RepositoryItem[]>([]);
  const [repositoriesLoading, setRepositoriesLoading] = useState<boolean>(true);
  const [repositoriesError, setRepositoriesError] = useState<string | null>(null);
  const [selectedRepository, setSelectedRepository] = useState<RepositoryItem | null>(null);

  const [reviews, setReviews] = useState<ReviewSummaryItem[]>([]);
  const [reviewsLoading, setReviewsLoading] = useState<boolean>(false);
  const [reviewsError, setReviewsError] = useState<string | null>(null);
  const [selectedReviewId, setSelectedReviewId] = useState<string | null>(null);

  const [reviewDetail, setReviewDetail] = useState<ReviewDetail | null>(null);
  const [reviewDetailLoading, setReviewDetailLoading] = useState<boolean>(false);
  const [reviewDetailError, setReviewDetailError] = useState<string | null>(null);

  const loadSummary = useCallback(async () => {
    try {
      setSummaryLoading(true);
      setSummaryError(null);
      const data = await dashboardService.getSummary();
      setSummary(data);
    } catch (err) {
      setSummaryError(
        extractErrorMessage(err, 'Não foi possível consultar o resumo do dashboard.'),
      );
    } finally {
      setSummaryLoading(false);
    }
  }, []);

  const loadRepositories = useCallback(async () => {
    try {
      setRepositoriesLoading(true);
      setRepositoriesError(null);
      const page = await repositoryService.listRepositories(0, 50);
      setRepositories(page.items);
      setSelectedRepository((current) => {
        if (current) {
          const updated = page.items.find((r) => r.id === current.id);
          if (updated) {
            return updated;
          }
        }
        return page.items[0] ?? null;
      });
    } catch (err) {
      setRepositoriesError(
        extractErrorMessage(err, 'Não foi possível carregar os repositórios.'),
      );
    } finally {
      setRepositoriesLoading(false);
    }
  }, []);

  const loadReviewsForRepository = useCallback(async (repositoryId: string) => {
    try {
      setReviewsLoading(true);
      setReviewsError(null);
      const page = await reviewService.listByRepository(repositoryId, 0, 50);
      setReviews(page.items);
      setSelectedReviewId((currentId) => {
        if (currentId && page.items.some((r) => r.id === currentId)) {
          return currentId;
        }
        return page.items[0]?.id ?? null;
      });
    } catch (err) {
      setReviewsError(
        extractErrorMessage(err, 'Não foi possível carregar as revisões do repositório.'),
      );
    } finally {
      setReviewsLoading(false);
    }
  }, []);

  const loadReviewDetail = useCallback(async (reviewId: string) => {
    try {
      setReviewDetailLoading(true);
      setReviewDetailError(null);
      const detail = await reviewService.getDetail(reviewId);
      setReviewDetail(detail);
    } catch (err) {
      setReviewDetailError(
        extractErrorMessage(err, 'Não foi possível carregar os detalhes da revisão.'),
      );
    } finally {
      setReviewDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSummary();
    void loadRepositories();
  }, [loadSummary, loadRepositories]);

  useEffect(() => {
    if (!selectedRepository) {
      setReviews([]);
      setSelectedReviewId(null);
      setReviewDetail(null);
      return;
    }
    void loadReviewsForRepository(selectedRepository.id);
  }, [selectedRepository, loadReviewsForRepository]);

  useEffect(() => {
    if (!selectedReviewId) {
      setReviewDetail(null);
      return;
    }
    void loadReviewDetail(selectedReviewId);
  }, [selectedReviewId, loadReviewDetail]);

  const handleRefreshAll = useCallback(() => {
    void loadSummary();
    void loadRepositories();
    if (selectedRepository) {
      void loadReviewsForRepository(selectedRepository.id);
    }
    if (selectedReviewId) {
      void loadReviewDetail(selectedReviewId);
    }
  }, [
    loadSummary,
    loadRepositories,
    loadReviewsForRepository,
    loadReviewDetail,
    selectedRepository,
    selectedReviewId,
  ]);

  const isRefreshing =
    summaryLoading || repositoriesLoading || reviewsLoading || reviewDetailLoading;

  return (
    <AppLayout onRefreshAll={handleRefreshAll} isRefreshing={isRefreshing}>
      <DashboardSummarySection
        summary={summary}
        loading={summaryLoading}
        error={summaryError}
        onRetry={() => void loadSummary()}
      />

      <div className="workspace-grid">
        <div className="workspace-sidebar">
          <RepositoryListSection
            repositories={repositories}
            selectedRepositoryId={selectedRepository?.id ?? null}
            loading={repositoriesLoading}
            error={repositoriesError}
            onSelectRepository={(repo) => {
              setSelectedRepository(repo);
              setSelectedReviewId(null);
            }}
            onRepositoryRegistered={(created) => {
              setRepositories((prev) => [created, ...prev]);
              setSelectedRepository(created);
              void loadSummary();
            }}
            onRetry={() => void loadRepositories()}
          />

          <RepositoryReviewsSection
            repository={selectedRepository}
            reviews={reviews}
            selectedReviewId={selectedReviewId}
            loading={reviewsLoading}
            error={reviewsError}
            onSelectReview={(reviewId) => setSelectedReviewId(reviewId)}
            onRetry={() => {
              if (selectedRepository) {
                void loadReviewsForRepository(selectedRepository.id);
              }
            }}
          />
        </div>

        <div className="workspace-content">
          <ReviewDetailSection
            review={reviewDetail}
            loading={reviewDetailLoading}
            error={reviewDetailError}
            onRetry={() => {
              if (selectedReviewId) {
                void loadReviewDetail(selectedReviewId);
              }
            }}
            onDecisionRecorded={() => {
              void loadSummary();
              if (selectedRepository) {
                void loadReviewsForRepository(selectedRepository.id);
              }
              if (selectedReviewId) {
                void loadReviewDetail(selectedReviewId);
              }
            }}
          />
        </div>
      </div>
    </AppLayout>
  );
}
export default App;

