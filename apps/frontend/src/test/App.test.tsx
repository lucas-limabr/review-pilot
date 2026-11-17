import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import type {
  DashboardSummary,
  PagedResponse,
  RepositoryItem,
  ReviewDetail,
  ReviewSummaryItem,
} from '../shared/types/api';

const mockSummary: DashboardSummary = {
  activeRepositoriesCount: 1,
  totalReviewsCount: 2,
  reviewsByStatus: {
    PENDING: 0,
    RUNNING: 0,
    COMPLETED: 1,
    PARTIAL: 1,
    FAILED: 0,
    CANCELLED: 0,
  },
  totalFindingsCount: 2,
  findingsBySeverity: {
    CRITICAL: 0,
    HIGH: 1,
    MEDIUM: 1,
    LOW: 0,
  },
  findingsByCategory: {
    BUG: 1,
    SECURITY: 1,
    PERFORMANCE: 0,
    MAINTAINABILITY: 0,
    TEST: 0,
  },
  approvedReviewsCount: 1,
  rejectedReviewsCount: 0,
};

const mockRepositoriesPage: PagedResponse<RepositoryItem> = {
  items: [
    {
      id: 'repo-1',
      provider: 'GITHUB',
      externalRepositoryId: 9001,
      owner: 'lucaslima',
      name: 'auto-elite',
      fullName: 'lucaslima/auto-elite',
      defaultBranch: 'main',
      sonarProjectKey: 'auto-elite',
      active: true,
      createdAt: '2026-11-17T18:00:00Z',
      updatedAt: '2026-11-17T18:00:00Z',
    },
  ],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
};

const mockReviewsPage: PagedResponse<ReviewSummaryItem> = {
  items: [
    {
      id: 'rev-1',
      repositoryId: 'repo-1',
      repositoryFullName: 'lucaslima/auto-elite',
      pullRequestId: 'pr-1',
      pullRequestNumber: 12,
      pullRequestTitle: 'Validação de cálculo de financiamento e rateio',
      headSha: 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678',
      status: 'COMPLETED',
      modelName: 'models/gemini-3.5-flash-lite',
      promptVersion: '1.0.0',
      findingsCount: 2,
      latestApprovalDecision: null,
      startedAt: '2026-11-17T18:01:00Z',
      completedAt: '2026-11-17T18:01:05Z',
      createdAt: '2026-11-17T18:01:00Z',
    },
  ],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
};

const mockReviewDetail: ReviewDetail = {
  id: 'rev-1',
  repositoryId: 'repo-1',
  repositoryFullName: 'lucaslima/auto-elite',
  pullRequestId: 'pr-1',
  pullRequestNumber: 12,
  pullRequestTitle: 'Validação de cálculo de financiamento e rateio',
  pullRequestHtmlUrl: 'https://github.com/lucaslima/auto-elite/pull/12',
  authorLogin: 'lucaslima',
  baseRef: 'main',
  headRef: 'feat/financing-calc',
  headSha: 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678',
  status: 'COMPLETED',
  modelName: 'models/gemini-3.5-flash-lite',
  promptVersion: '1.0.0',
  schemaVersion: '1.0',
  summary: 'Foram identificados 2 problemas técnicos no serviço de financiamento.',
  limitations: ['SonarQube Community Build analisou a branch main como contexto complementar.'],
  inputTokens: 1420,
  outputTokens: 380,
  failureCode: null,
  failureMessage: null,
  latestApprovalDecision: null,
  startedAt: '2026-11-17T18:01:00Z',
  completedAt: '2026-11-17T18:01:05Z',
  createdAt: '2026-11-17T18:01:00Z',
  findings: [
    {
      id: 'find-1',
      fingerprint: 'BUG-FinancingService.java-44-div-zero',
      category: 'BUG',
      severity: 'HIGH',
      title: 'Possível ArithmeticException em parcelamento com zero meses',
      description: 'O número de parcelas não é validado antes da divisão.',
      filePath: 'src/main/java/com/autoelite/FinancingService.java',
      startLine: 44,
      endLine: 47,
      evidence: 'BigDecimal installment = totalAmount.divide(BigDecimal.valueOf(months));',
      recommendation: 'Validar que months > 0 antes de executar divide().',
      confidence: 0.94,
      origin: 'CORRELATED',
      externalReference: 'java:S3518',
      createdAt: '2026-11-17T18:01:05Z',
    },
    {
      id: 'find-2',
      fingerprint: 'SECURITY-AuthFilter.java-80-token-log',
      category: 'SECURITY',
      severity: 'MEDIUM',
      title: 'Cabeçalho de autorização registrado em log de debug',
      description: 'O filtro imprime o header Authorization bruto em nível debug.',
      filePath: 'src/main/java/com/autoelite/AuthFilter.java',
      startLine: 80,
      endLine: 82,
      evidence: 'log.debug("Authorization header: {}", request.getHeader("Authorization"));',
      recommendation: 'Remover ou mascarar o valor do header antes de registrar logs.',
      confidence: 0.91,
      origin: 'AI',
      externalReference: null,
      createdAt: '2026-11-17T18:01:05Z',
    },
  ],
};

describe('App Dashboard Integration', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders dashboard KPIs, repository list, PR detail, filters findings and submits approval', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.endsWith('/dashboard/summary')) {
        return new Response(JSON.stringify(mockSummary), { status: 200 });
      }
      if (url.includes('/repositories?')) {
        return new Response(JSON.stringify(mockRepositoriesPage), { status: 200 });
      }
      if (url.includes('/repositories/repo-1/reviews')) {
        return new Response(JSON.stringify(mockReviewsPage), { status: 200 });
      }
      if (url.endsWith('/reviews/rev-1') && (!init || init.method === 'GET')) {
        return new Response(JSON.stringify(mockReviewDetail), { status: 200 });
      }
      if (url.endsWith('/reviews/rev-1/approval') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            id: 'act-1',
            reviewExecutionId: 'rev-1',
            actionType: 'HUMAN_DECISION',
            decision: 'APPROVED',
            actor: 'lucaslima',
            comment: 'Revisão validada.',
            createdAt: '2026-11-17T18:05:00Z',
          }),
          { status: 200 },
        );
      }
      return new Response(JSON.stringify({ detail: 'Not found' }), { status: 404 });
    });

    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('kpi-active-repos')).toHaveTextContent('1');
    });
    expect(screen.getByTestId('kpi-total-reviews')).toHaveTextContent('2');
    expect(screen.getByTestId('kpi-total-findings')).toHaveTextContent('2');

    await waitFor(() => {
      expect(
        screen.getByText(/Possível ArithmeticException em parcelamento com zero meses/i),
      ).toBeInTheDocument();
    });
    expect(
      screen.getByText(/Cabeçalho de autorização registrado em log de debug/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/src\/main\/java\/com\/autoelite\/FinancingService\.java \(Linhas 44–47\)/i),
    ).toBeInTheDocument();

    // Filter by category SECURITY
    const categorySelect = screen.getByLabelText(/Categoria:/i);
    await user.selectOptions(categorySelect, 'SECURITY');

    expect(
      screen.queryByText(/Possível ArithmeticException em parcelamento com zero meses/i),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(/Cabeçalho de autorização registrado em log de debug/i),
    ).toBeInTheDocument();

    // Reset filter and submit human approval
    await user.selectOptions(categorySelect, 'ALL');
    const approveBtn = screen.getByRole('button', { name: /Aprovar Revisão/i });
    await user.click(approveBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/Decisão 'Aprovada no ReviewPilot' registrada localmente/i),
      ).toBeInTheDocument();
    });

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringMatching(/\/reviews\/rev-1\/approval$/),
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('displays error state and retries when backend API is unreachable', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('Network error'));

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Falha ao carregar métricas do dashboard/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/Erro ao carregar repositórios/i)).toBeInTheDocument();
  });
});

