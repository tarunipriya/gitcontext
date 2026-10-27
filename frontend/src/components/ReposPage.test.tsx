import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReposPage } from './ReposPage';
import type { JobResponse, RepoSummaryResponse } from '../api/client';

const mockRepos: RepoSummaryResponse[] = [
  {
    repo: 'tarunipriya/gitcontext',
    headCommitSha: 'abcdef1234567890abcdef1234567890abcdef12',
    countsBySourceType: { CODE: 12, MARKDOWN: 3 },
    totalChunks: 45,
    chunkCountsByType: { FUNCTION: 30, CLASS: 15 },
    avgChunkTokens: 120.5,
    maxChunkTokens: 450,
    indexedPoints: 45,
    embedMillis: 120,
    upsertMillis: 25,
    ingestedAt: '2026-10-27T10:00:00Z',
  },
  {
    repo: 'spring-projects/spring-petclinic',
    headCommitSha: '1234567890abcdef1234567890abcdef12345678',
    countsBySourceType: { CODE: 18, MARKDOWN: 2 },
    totalChunks: 80,
    chunkCountsByType: { FUNCTION: 60, CLASS: 20 },
    avgChunkTokens: 110.0,
    maxChunkTokens: 380,
    indexedPoints: 80,
    embedMillis: 150,
    upsertMillis: 30,
    ingestedAt: '2026-10-26T08:30:00Z',
  },
];

const mockJobRunning: JobResponse = {
  jobId: 'job-101',
  repo: 'owner/active-repo',
  status: 'RUNNING',
  submittedAt: '2026-10-27T10:00:00Z',
  startedAt: '2026-10-27T10:00:02Z',
};

describe('ReposPage Component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders loading skeleton and displays indexed repositories on success', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/repos')) {
        return Promise.resolve(
          new Response(JSON.stringify(mockRepos), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.includes('/api/ingestions')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unhandled URL: ${url}`));
    });

    render(<ReposPage />);

    expect(screen.getByTestId('repos-loading-skeleton')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('tarunipriya/gitcontext')).toBeInTheDocument();
      expect(screen.getByText('spring-projects/spring-petclinic')).toBeInTheDocument();
    });

    expect(screen.getByText('45')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
  });

  it('renders empty state when no repositories are indexed', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/repos')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.includes('/api/ingestions')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unhandled URL: ${url}`));
    });

    render(<ReposPage />);

    await waitFor(() => {
      expect(screen.getByTestId('repos-empty-state')).toBeInTheDocument();
    });
    expect(screen.getByText(/No repositories indexed yet/i)).toBeInTheDocument();
  });

  it('validates repository format client-side before submitting', async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/repos') || url.includes('/api/ingestions')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unhandled URL: ${url}`));
    });

    render(<ReposPage />);

    const input = screen.getByLabelText(/GitHub Repository/i);
    const submitBtn = screen.getByRole('button', { name: /ingest/i });

    // Invalid format: no slash
    await user.type(input, 'invalid-repo');
    await user.click(submitBtn);

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/Repository must be in "owner\/name" format/i)).toBeInTheDocument();

    // Verify POST /api/ingestions was never called
    const postCalls = fetchSpy.mock.calls.filter((call) => {
      const opts = call[1] as RequestInit | undefined;
      return opts?.method === 'POST';
    });
    expect(postCalls).toHaveLength(0);
  });

  it('submits valid repository and adds job to jobs list', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes('/api/repos')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.endsWith('/api/ingestions') && init?.method === 'POST') {
        const body = JSON.parse(init.body as string);
        expect(body.repo).toBe('owner/active-repo');
        return Promise.resolve(
          new Response(JSON.stringify(mockJobRunning), {
            status: 202,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.endsWith('/api/ingestions')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.includes('/api/ingestions/job-101')) {
        return Promise.resolve(
          new Response(JSON.stringify(mockJobRunning), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unhandled URL: ${url}`));
    });

    render(<ReposPage />);

    const input = screen.getByLabelText(/GitHub Repository/i);
    const submitBtn = screen.getByRole('button', { name: /ingest/i });

    await user.type(input, 'https://github.com/owner/active-repo.git');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('owner/active-repo')).toBeInTheDocument();
      expect(screen.getByText('RUNNING')).toBeInTheDocument();
    });
  });

  it('displays 409 Conflict ProblemDetail error when job is already running', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/ingestions') && init?.method === 'POST') {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              title: 'Conflict',
              status: 409,
              detail: 'Ingestion is already in progress for repository owner/active-repo',
            }),
            {
              status: 409,
              headers: { 'Content-Type': 'application/problem+json' },
            }
          )
        );
      }
      return Promise.resolve(
        new Response(JSON.stringify([]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      );
    });

    render(<ReposPage />);

    const input = screen.getByLabelText(/GitHub Repository/i);
    const submitBtn = screen.getByRole('button', { name: /ingest/i });

    await user.type(input, 'owner/active-repo');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByTestId('ingest-error-banner')).toBeInTheDocument();
    });

    expect(screen.getByText('Conflict')).toBeInTheDocument();
    expect(screen.getByText(/Ingestion is already in progress/i)).toBeInTheDocument();
    expect(screen.getByText('HTTP 409')).toBeInTheDocument();
  });

  it('displays 429 Rate Limit and 503 Service Unavailable ProblemDetail errors', async () => {
    const user = userEvent.setup();
    let currentStatus = 429;
    let currentDetail = 'Rate limit reached. Please wait before triggering another ingestion.';

    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/ingestions') && init?.method === 'POST') {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              title: currentStatus === 429 ? 'Rate Limit Exceeded' : 'Service Unavailable',
              status: currentStatus,
              detail: currentDetail,
            }),
            {
              status: currentStatus,
              headers: { 'Content-Type': 'application/problem+json' },
            }
          )
        );
      }
      return Promise.resolve(
        new Response(JSON.stringify([]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      );
    });

    render(<ReposPage />);

    const input = screen.getByLabelText(/GitHub Repository/i);
    const submitBtn = screen.getByRole('button', { name: /ingest/i });

    // Test 429
    await user.type(input, 'owner/repo-one');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('Rate Limit Exceeded')).toBeInTheDocument();
      expect(screen.getByText('HTTP 429')).toBeInTheDocument();
    });

    // Test 503
    currentStatus = 503;
    currentDetail = 'Ingestion queue is full. Try again shortly.';
    await user.clear(input);
    await user.type(input, 'owner/repo-two');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('Service Unavailable')).toBeInTheDocument();
      expect(screen.getByText('HTTP 503')).toBeInTheDocument();
    });
  });

  it('calls onAskRepo when Ask button on a repository card is clicked', async () => {
    const user = userEvent.setup();
    const handleAskRepo = vi.fn();

    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/repos')) {
        return Promise.resolve(
          new Response(JSON.stringify(mockRepos), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.resolve(
        new Response(JSON.stringify([]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      );
    });

    render(<ReposPage onAskRepo={handleAskRepo} />);

    await waitFor(() => {
      expect(screen.getByText('tarunipriya/gitcontext')).toBeInTheDocument();
    });

    const askBtn = screen.getByTestId('ask-repo-tarunipriya/gitcontext');
    await user.click(askBtn);

    expect(handleAskRepo).toHaveBeenCalledWith('tarunipriya/gitcontext');
  });
});

describe('ReposPage Polling Logic with Fake Timers', () => {
  const flushPromises = async () => {
    for (let i = 0; i < 20; i++) {
      await Promise.resolve();
    }
  };

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'] });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('polls GET /api/ingestions/{jobId} every 2 s while RUNNING, and stops when COMPLETED', async () => {
    let pollCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/repos')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.endsWith('/api/ingestions')) {
        return Promise.resolve(
          new Response(JSON.stringify([mockJobRunning]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.includes('/api/ingestions/job-101')) {
        pollCount++;
        const isDone = pollCount >= 3;
        const currentJob: JobResponse = {
          ...mockJobRunning,
          status: isDone ? 'COMPLETED' : 'RUNNING',
          summary: isDone ? 'Done indexing' : undefined,
        };
        return Promise.resolve(
          new Response(JSON.stringify(currentJob), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unhandled URL: ${url}`));
    });

    render(<ReposPage />);

    // Initial load of recent jobs
    await act(async () => {
      await vi.advanceTimersByTimeAsync(50);
      await flushPromises();
    });

    expect(screen.getByText('owner/active-repo')).toBeInTheDocument();
    expect(screen.getByText('RUNNING')).toBeInTheDocument();
    expect(pollCount).toBe(0);

    // Advance 2s -> first poll
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
      await flushPromises();
    });
    expect(pollCount).toBe(1);

    // Advance another 2s -> second poll
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
      await flushPromises();
    });
    expect(pollCount).toBe(2);

    // Third poll returns COMPLETED
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
      await flushPromises();
    });
    expect(pollCount).toBe(3);

    // Check that status updated in UI
    expect(screen.getByText('COMPLETED')).toBeInTheDocument();

    // Advance another 4 seconds; polling should have stopped since status is COMPLETED
    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
      await flushPromises();
    });
    expect(pollCount).toBe(3);
  });

  it('asserts getJob is called about once per 2 s (3 calls after 6 s) and ceases after COMPLETED or unmount', async () => {
    let getJobCalls = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/repos')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.endsWith('/api/ingestions')) {
        return Promise.resolve(
          new Response(JSON.stringify([mockJobRunning]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.includes('/api/ingestions/job-101')) {
        getJobCalls++;
        // Return RUNNING for the first 3 calls, and COMPLETED on the 4th call
        const isCompleted = getJobCalls >= 4;
        const job: JobResponse = {
          ...mockJobRunning,
          status: isCompleted ? 'COMPLETED' : 'RUNNING',
          summary: isCompleted ? 'Indexed chunks successfully' : undefined,
        };
        return Promise.resolve(
          new Response(JSON.stringify(job), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unhandled URL: ${url}`));
    });

    const { unmount } = render(<ReposPage />);

    // Initial load
    await act(async () => {
      await vi.advanceTimersByTimeAsync(50);
      await flushPromises();
    });
    expect(getJobCalls).toBe(0);

    // Advance 6s: should trigger exactly 3 polls (at 2s, 4s, 6s)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
      await flushPromises();
    });
    expect(getJobCalls).toBe(3);

    // Advance 2s more (8s total): 4th poll triggers and returns COMPLETED
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
      await flushPromises();
    });
    expect(getJobCalls).toBe(4);
    expect(screen.getByText('COMPLETED')).toBeInTheDocument();

    // Advance another 6s: no further getJob calls occur after COMPLETED
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
      await flushPromises();
    });
    expect(getJobCalls).toBe(4);

    // Unmount and verify no calls occur after unmount
    unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
      await flushPromises();
    });
    expect(getJobCalls).toBe(4);
  });

  it('stops polling when job is FAILED and displays the failure error message', async () => {
    let pollCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/repos')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.endsWith('/api/ingestions')) {
        return Promise.resolve(
          new Response(JSON.stringify([mockJobRunning]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.includes('/api/ingestions/job-101')) {
        pollCount++;
        const isFailed = pollCount >= 2;
        const currentJob: JobResponse = {
          ...mockJobRunning,
          status: isFailed ? 'FAILED' : 'RUNNING',
          errorMessage: isFailed ? 'Git clone failed: repository not found' : undefined,
        };
        return Promise.resolve(
          new Response(JSON.stringify(currentJob), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unhandled URL: ${url}`));
    });

    render(<ReposPage />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(50);
      await flushPromises();
    });

    expect(screen.getByText('RUNNING')).toBeInTheDocument();

    // First poll: still running
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
      await flushPromises();
    });
    expect(pollCount).toBe(1);

    // Second poll: fails the job
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
      await flushPromises();
    });
    expect(pollCount).toBe(2);

    expect(screen.getByText('FAILED')).toBeInTheDocument();
    expect(screen.getByText(/Git clone failed: repository not found/i)).toBeInTheDocument();

    // Advance 4 seconds: should NOT poll anymore
    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
      await flushPromises();
    });
    expect(pollCount).toBe(2);
  });

  it('stops polling on unmount and clears timers', async () => {
    let pollCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/repos')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.endsWith('/api/ingestions')) {
        return Promise.resolve(
          new Response(JSON.stringify([mockJobRunning]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.includes('/api/ingestions/job-101')) {
        pollCount++;
        return Promise.resolve(
          new Response(JSON.stringify(mockJobRunning), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unhandled URL: ${url}`));
    });

    const { unmount } = render(<ReposPage />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(50);
      await flushPromises();
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
      await flushPromises();
    });
    expect(pollCount).toBe(1);

    // Unmount component
    unmount();

    // Advance time after unmount
    await act(async () => {
      await vi.advanceTimersByTimeAsync(6000);
      await flushPromises();
    });
    expect(pollCount).toBe(1);
  });

  it('pauses polling when document.visibilityState is hidden and resumes when visible', async () => {
    let pollCount = 0;

    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/repos')) {
        return Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.endsWith('/api/ingestions')) {
        return Promise.resolve(
          new Response(JSON.stringify([mockJobRunning]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      if (url.includes('/api/ingestions/job-101')) {
        pollCount++;
        return Promise.resolve(
          new Response(JSON.stringify(mockJobRunning), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unhandled URL: ${url}`));
    });

    render(<ReposPage />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(50);
      await flushPromises();
    });

    // Visible: polls every 2s
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
      await flushPromises();
    });
    expect(pollCount).toBe(1);

    // Simulate tab hidden
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'hidden',
    });

    // Advance 4s while hidden: should NOT poll
    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
      await flushPromises();
    });
    expect(pollCount).toBe(1);

    // Simulate tab becoming visible again
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    });
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await flushPromises();
    });

    // Immediate poll triggered on visibility change
    expect(pollCount).toBe(2);

    // Regular interval resumes
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
      await flushPromises();
    });
    expect(pollCount).toBe(3);
  });
});
