import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  askRepo,
  submitIngestion,
  getJob,
  getRecentJobs,
  getRepoSummary,
  getRepos,
  ApiError,
  type AskResponse,
  type JobResponse,
  type RepoSummaryResponse,
} from './client';

describe('API Client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('askRepo', () => {
    it('sends POST request with encoded owner and name and returns parsed response', async () => {
      const mockResult: AskResponse = {
        question: 'What is this?',
        answer: 'This is GitContext.',
        citations: [],
        sources: [],
        model: 'gemini-3.5-flash',
        promptTokens: 100,
        outputTokens: 50,
        retrievalMillis: 20,
        generationMillis: 800,
        totalMillis: 820,
        warnings: [],
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify(mockResult), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        )
      );

      const res = await askRepo('tarunipriya', 'gitcontext', {
        question: 'What is this?',
        mode: 'HYBRID',
      });

      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/repos/tarunipriya/gitcontext/ask',
        expect.objectContaining({
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
          },
          body: JSON.stringify({ question: 'What is this?', mode: 'HYBRID' }),
        })
      );
      expect(res).toEqual(mockResult);
    });

    it('throws ApiError with ProblemDetail data on 404 response', async () => {
      vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
        Promise.resolve(
          new Response(
            JSON.stringify({
              title: 'Resource Not Found',
              status: 404,
              detail: 'Repository not found',
            }),
            {
              status: 404,
              headers: { 'Content-Type': 'application/problem+json' },
            }
          )
        )
      );

      let thrownError: ApiError | null = null;
      try {
        await askRepo('unknown', 'repo', { question: 'test' });
      } catch (e) {
        thrownError = e as ApiError;
      }

      expect(thrownError).toBeInstanceOf(ApiError);
      expect(thrownError?.status).toBe(404);
      expect(thrownError?.title).toBe('Resource Not Found');
      expect(thrownError?.detail).toBe('Repository not found');
      expect(thrownError?.message).toBe('Repository not found');
    });

    it('throws ApiError with status message when response body is not JSON', async () => {
      vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
        Promise.resolve(
          new Response('Bad Gateway', {
            status: 502,
            headers: { 'Content-Type': 'text/plain' },
          })
        )
      );

      try {
        await askRepo('owner', 'repo', { question: 'test' });
        expect.unreachable('Should have thrown');
      } catch (e) {
        const err = e as ApiError;
        expect(err).toBeInstanceOf(ApiError);
        expect(err.status).toBe(502);
        expect(err.message).toBe('Request failed with HTTP 502');
      }
    });
  });

  describe('ingestion endpoints', () => {
    it('submitIngestion calls POST /api/ingestions', async () => {
      const mockJob: JobResponse = {
        jobId: '123e4567-e89b-12d3-a456-426614174000',
        repo: 'owner/repo',
        status: 'SUBMITTED',
        submittedAt: '2026-10-25T12:00:00Z',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify(mockJob), {
            status: 202,
            headers: { 'Content-Type': 'application/json' },
          })
        )
      );

      const res = await submitIngestion('owner/repo');
      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/ingestions',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ repo: 'owner/repo' }),
        })
      );
      expect(res).toEqual(mockJob);
    });

    it('getJob calls GET /api/ingestions/:jobId', async () => {
      const mockJob: JobResponse = {
        jobId: 'job-123',
        repo: 'owner/repo',
        status: 'COMPLETED',
        submittedAt: '2026-10-25T12:00:00Z',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify(mockJob), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        )
      );

      const res = await getJob('job-123');
      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/ingestions/job-123',
        expect.objectContaining({ headers: { Accept: 'application/json' } })
      );
      expect(res).toEqual(mockJob);
    });

    it('getRecentJobs calls GET /api/ingestions', async () => {
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        )
      );

      const res = await getRecentJobs();
      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/ingestions',
        expect.objectContaining({ headers: { Accept: 'application/json' } })
      );
      expect(res).toEqual([]);
    });

    it('getRepoSummary calls GET /api/repos/:owner/:name/summary', async () => {
      const mockSummary: RepoSummaryResponse = {
        repo: 'owner/repo',
        headCommitSha: 'abc1234',
        countsBySourceType: { CODE: 50 },
        totalChunks: 120,
        chunkCountsByType: { FUNCTION: 100 },
        avgChunkTokens: 250,
        maxChunkTokens: 800,
        indexedPoints: 120,
        embedMillis: 450,
        upsertMillis: 120,
        ingestedAt: '2026-10-25T12:00:00Z',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify(mockSummary), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        )
      );

      const res = await getRepoSummary('owner', 'repo');
      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/repos/owner/repo/summary',
        expect.objectContaining({ headers: { Accept: 'application/json' } })
      );
      expect(res).toEqual(mockSummary);
    });
  });

  describe('getRepos', () => {
    it('fetches /api/repos and returns array of repo summaries', async () => {
      const mockRepos: RepoSummaryResponse[] = [
        {
          repo: 'tarunipriya/gitcontext',
          headCommitSha: 'def5678',
          countsBySourceType: { CODE: 100 },
          totalChunks: 250,
          chunkCountsByType: { METHOD: 200 },
          avgChunkTokens: 180,
          maxChunkTokens: 600,
          indexedPoints: 250,
          embedMillis: 800,
          upsertMillis: 200,
          ingestedAt: '2026-10-27T10:00:00Z',
        },
      ];

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
        Promise.resolve(
          new Response(JSON.stringify(mockRepos), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        )
      );

      const res = await getRepos();
      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/repos',
        expect.objectContaining({ headers: { Accept: 'application/json' } })
      );
      expect(res).toEqual(mockRepos);
    });
  });
});
