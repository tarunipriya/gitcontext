import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  submitIngestion,
  getJob,
  getRecentJobs,
  getRepos,
  ApiError,
  type JobResponse,
  type RepoSummaryResponse,
  type ProblemDetail,
} from '../api/client';
import { parseRepoRef, isValidRepoRef, formatElapsedTime, formatRelativeTime } from '../utils/formatters';
import { AppHeader } from './AppHeader';
import './ReposPage.css';

export interface ReposPageProps {
  onAskRepo?: (repo: string) => void;
}

export const ReposPage: React.FC<ReposPageProps> = ({ onAskRepo }) => {
  const [repoInput, setRepoInput] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submitError, setSubmitError] = useState<ProblemDetail | null>(null);

  const [jobs, setJobs] = useState<JobResponse[]>([]);
  const jobsRef = useRef<JobResponse[]>(jobs);
  jobsRef.current = jobs;

  const hasActiveJob = jobs.some((j) => j.status === 'QUEUED' || j.status === 'RUNNING');
  const pollInFlightRef = useRef<boolean>(false);

  const [repos, setRepos] = useState<RepoSummaryResponse[]>([]);
  const [reposLoading, setReposLoading] = useState<boolean>(true);
  const [reposError, setReposError] = useState<ProblemDetail | null>(null);

  const [currentTime, setCurrentTime] = useState<number>(() => Date.now());

  // Store in-flight AbortControllers so they can be aborted on unmount
  const inFlightControllersRef = useRef<Set<AbortController>>(new Set());

  const registerController = useCallback((controller: AbortController) => {
    inFlightControllersRef.current.add(controller);
    return controller;
  }, []);

  const unregisterController = useCallback((controller: AbortController) => {
    inFlightControllersRef.current.delete(controller);
  }, []);

  // Fetch indexed repositories
  const loadRepos = useCallback(async (signal?: AbortSignal) => {
    setReposLoading(true);
    setReposError(null);
    try {
      const data = await getRepos(signal);
      setRepos(data);
    } catch (err: unknown) {
      if (signal?.aborted) return;
      if (err instanceof ApiError) {
        setReposError(err.problemDetail || { title: err.title || 'Error', detail: err.detail || err.message, status: err.status });
      } else if (err instanceof Error) {
        setReposError({ title: 'Failed to load repositories', detail: err.message });
      }
    } finally {
      if (!signal?.aborted) {
        setReposLoading(false);
      }
    }
  }, []);

  // Fetch recent ingestion jobs
  const loadRecentJobs = useCallback(async (signal?: AbortSignal) => {
    try {
      const recent = await getRecentJobs(signal);
      setJobs(recent);
    } catch {
      // Gracefully ignore error on initial jobs list
    }
  }, []);

  // Initial load
  useEffect(() => {
    const controller = new AbortController();
    registerController(controller);

    loadRepos(controller.signal);
    loadRecentJobs(controller.signal);

    return () => {
      controller.abort();
      unregisterController(controller);
    };
  }, [loadRepos, loadRecentJobs, registerController, unregisterController]);

  // Tick elapsed time live: depends ONLY on hasActiveJob
  useEffect(() => {
    if (!hasActiveJob) return;

    const timer = setInterval(() => {
      setCurrentTime(Date.now());
    }, 1000);

    return () => clearInterval(timer);
  }, [hasActiveJob]);

  // Polling logic for active jobs:
  // Reads jobsRef.current; does not depend on `jobs` state.
  // Only updates state for jobs whose displayed fields actually changed.
  const pollActiveJobs = useCallback(async () => {
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      return;
    }

    if (pollInFlightRef.current) {
      return;
    }

    const currentJobs = jobsRef.current;
    const activeJobs = currentJobs.filter((j) => j.status === 'QUEUED' || j.status === 'RUNNING');
    if (activeJobs.length === 0) {
      return;
    }

    pollInFlightRef.current = true;
    let shouldReloadRepos = false;

    try {
      const results = await Promise.all(
        activeJobs.map(async (job) => {
          const controller = new AbortController();
          registerController(controller);

          try {
            const updated = await getJob(job.jobId, controller.signal);
            return { oldJob: job, updated };
          } catch {
            return null;
          } finally {
            unregisterController(controller);
          }
        })
      );

      const successfulUpdates = results.filter(
        (r): r is { oldJob: JobResponse; updated: JobResponse } => r !== null
      );

      for (const { oldJob, updated } of successfulUpdates) {
        if (updated.status === 'COMPLETED' && oldJob.status !== 'COMPLETED') {
          shouldReloadRepos = true;
        }
      }

      if (successfulUpdates.length > 0) {
        setJobs((prev) => {
          const updateMap = new Map(successfulUpdates.map((u) => [u.updated.jobId, u.updated]));
          let anyChanged = false;
          const next = prev.map((item) => {
            const up = updateMap.get(item.jobId);
            if (!up) return item;
            const changed =
              item.status !== up.status ||
              item.startedAt !== up.startedAt ||
              item.finishedAt !== up.finishedAt ||
              item.summary !== up.summary ||
              item.errorMessage !== up.errorMessage;
            if (changed) {
              anyChanged = true;
              return up;
            }
            return item;
          });
          return anyChanged ? next : prev;
        });
      }

      if (shouldReloadRepos) {
        const reposController = new AbortController();
        registerController(reposController);
        loadRepos(reposController.signal).finally(() => unregisterController(reposController));
      }
    } finally {
      pollInFlightRef.current = false;
    }
  }, [registerController, unregisterController, loadRepos]);

  // One polling effect that depends ONLY on hasActiveJob
  useEffect(() => {
    if (!hasActiveJob) {
      return;
    }

    const intervalId = setInterval(() => {
      pollActiveJobs();
    }, 2000);

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        pollActiveJobs();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      clearInterval(intervalId);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [hasActiveJob, pollActiveJobs]);

  // Abort all in-flight requests on unmount
  useEffect(() => {
    const controllers = inFlightControllersRef.current;
    return () => {
      controllers.forEach((c) => c.abort());
      controllers.clear();
    };
  }, []);

  // Form submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = repoInput.trim();
    if (!trimmed || submitting) {
      return;
    }

    // Client-side validation using the same pattern as backend RepoRef
    if (!isValidRepoRef(trimmed)) {
      setSubmitError({
        title: 'Invalid Repository',
        detail: 'Repository must be in "owner/name" format or GitHub URL (e.g. tarunipriya/gitcontext)',
        status: 400,
      });
      return;
    }

    const parsed = parseRepoRef(trimmed);
    if (!parsed) {
      setSubmitError({
        title: 'Invalid Repository',
        detail: 'Repository must be in "owner/name" format (e.g. tarunipriya/gitcontext)',
        status: 400,
      });
      return;
    }

    setSubmitting(true);
    setSubmitError(null);

    const controller = new AbortController();
    registerController(controller);

    try {
      const job = await submitIngestion(parsed.fullName, controller.signal);
      setJobs((prev) => [job, ...prev.filter((j) => j.jobId !== job.jobId)]);
      setRepoInput('');
    } catch (err: unknown) {
      if (controller.signal.aborted) return;
      if (err instanceof ApiError) {
        setSubmitError(err.problemDetail || {
          title: err.title || `Request failed (${err.status})`,
          detail: err.detail || err.message,
          status: err.status,
        });
      } else if (err instanceof Error) {
        setSubmitError({
          title: 'Ingestion Request Failed',
          detail: err.message,
        });
      }
    } finally {
      unregisterController(controller);
      setSubmitting(false);
    }
  };

  const handleAskRepo = (repoName: string) => {
    if (onAskRepo) {
      onAskRepo(repoName);
    } else {
      window.location.hash = `#/ask?repo=${encodeURIComponent(repoName)}`;
    }
  };

  return (
    <div className="ask-container repos-container">
      <AppHeader currentTab="repos" />

      <main className="ask-main">
        {/* Ingestion Submission Form */}
        <section className="ingest-card" aria-labelledby="ingest-heading">
          <div className="section-header">
            <div>
              <h2 id="ingest-heading" className="section-title">Ingest Repository</h2>
              <p className="section-subtitle">
                Index any GitHub repository for hybrid retrieval, chunking, and grounded question answering.
              </p>
            </div>
          </div>

          <form className="ingest-form" onSubmit={handleSubmit} noValidate>
            <div className="form-group">
              <label htmlFor="repo-ingest-input" className="form-label">
                GitHub Repository
              </label>
              <div className="input-with-button">
                <input
                  id="repo-ingest-input"
                  type="text"
                  className="form-input"
                  value={repoInput}
                  onChange={(e) => {
                    setRepoInput(e.target.value);
                    if (submitError) setSubmitError(null);
                  }}
                  placeholder="owner/name (e.g. tarunipriya/gitcontext) or GitHub URL"
                  disabled={submitting}
                  autoComplete="off"
                  spellCheck="false"
                  required
                />
                <button
                  id="ingest-button"
                  type="submit"
                  className="ask-button ingest-submit-button"
                  disabled={submitting || repoInput.trim().length === 0}
                >
                  {submitting ? (
                    <>
                      <span className="spinner" aria-hidden="true" />
                      <span>Submitting...</span>
                    </>
                  ) : (
                    <>
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <polyline points="16 16 12 12 8 16" />
                        <line x1="12" y1="12" x2="12" y2="21" />
                        <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" />
                      </svg>
                      <span>Ingest</span>
                    </>
                  )}
                </button>
              </div>
            </div>

            {submitError && (
              <div className="error-banner" role="alert" data-testid="ingest-error-banner">
                <div className="error-icon" aria-hidden="true">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="12" r="10" />
                    <line x1="12" y1="8" x2="12" y2="12" />
                    <line x1="12" y1="16" x2="12.01" y2="16" />
                  </svg>
                </div>
                <div className="error-content">
                  <div className="error-header-line">
                    <strong className="error-title">{submitError.title || 'Error'}</strong>
                    {submitError.status && (
                      <span className="error-status-badge">HTTP {submitError.status}</span>
                    )}
                  </div>
                  {submitError.detail && <p className="error-detail">{submitError.detail}</p>}
                </div>
              </div>
            )}
          </form>
        </section>

        {/* Active & Recent Ingestion Jobs */}
        {jobs.length > 0 && (
          <section className="jobs-section" aria-labelledby="jobs-heading">
            <div className="section-header">
              <h2 id="jobs-heading" className="section-title">
                Ingestion Jobs ({jobs.length})
              </h2>
            </div>

            <div className="jobs-list" role="list">
              {jobs.map((job) => {
                const elapsed = formatElapsedTime(
                  job.startedAt || job.submittedAt,
                  job.finishedAt,
                  currentTime
                );
                return (
                  <article key={job.jobId} className={`job-card job-status-${job.status.toLowerCase()}`} role="listitem">
                    <div className="job-card-header">
                      <div className="job-repo-info">
                        <span className="job-repo-name">{job.repo}</span>
                        <span className="job-elapsed-time" title="Elapsed duration">
                          ⏱ {elapsed}
                        </span>
                      </div>
                      <span className={`status-badge status-${job.status.toLowerCase()}`}>
                        {job.status === 'RUNNING' && <span className="status-spinner" aria-hidden="true" />}
                        {job.status === 'COMPLETED' && <span className="status-icon" aria-hidden="true">✓</span>}
                        {job.status === 'FAILED' && <span className="status-icon" aria-hidden="true">✕</span>}
                        {job.status === 'QUEUED' && <span className="status-icon" aria-hidden="true">⏳</span>}
                        <span>{job.status}</span>
                      </span>
                    </div>

                    {job.status === 'FAILED' && job.errorMessage && (
                      <div className="job-error-message" role="alert">
                        <strong>Failure:</strong> {job.errorMessage}
                      </div>
                    )}

                    {job.status === 'COMPLETED' && job.summary && (
                      <div className="job-summary-message">
                        {job.summary}
                      </div>
                    )}
                  </article>
                );
              })}
            </div>
          </section>
        )}

        {/* Indexed Repositories List */}
        <section className="indexed-repos-section" aria-labelledby="indexed-repos-heading">
          <div className="section-header">
            <div>
              <h2 id="indexed-repos-heading" className="section-title">
                Indexed Repositories
              </h2>
              <p className="section-subtitle">
                Repositories ready for natural language grounded question answering.
              </p>
            </div>
          </div>

          {reposLoading && (
            <div className="repos-grid" data-testid="repos-loading-skeleton">
              {[1, 2, 3].map((i) => (
                <div key={i} className="repo-card repo-card-skeleton shimmer" aria-hidden="true" />
              ))}
            </div>
          )}

          {!reposLoading && reposError && (
            <div className="error-banner" role="alert">
              <div className="error-content">
                <strong className="error-title">{reposError.title || 'Error Loading Repositories'}</strong>
                {reposError.detail && <p className="error-detail">{reposError.detail}</p>}
              </div>
            </div>
          )}

          {!reposLoading && !reposError && repos.length === 0 && (
            <div className="empty-state" data-testid="repos-empty-state">
              <div className="empty-state-icon" aria-hidden="true">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                  <line x1="12" y1="11" x2="12" y2="17" />
                  <line x1="9" y1="14" x2="15" y2="14" />
                </svg>
              </div>
              <h3 className="empty-state-title">No repositories indexed yet</h3>
              <p className="empty-state-desc">
                Enter a GitHub repository name above to ingest code files, PRs, and commits into the search index.
              </p>
            </div>
          )}

          {!reposLoading && !reposError && repos.length > 0 && (
            <div className="repos-grid" data-testid="repos-grid">
              {repos.map((repoItem) => {
                const codeCount = repoItem.countsBySourceType?.CODE;
                return (
                  <article key={repoItem.repo} className="repo-card" data-testid={`repo-card-${repoItem.repo}`}>
                    <div className="repo-card-body">
                      <div className="repo-card-top">
                        <span className="repo-card-icon" aria-hidden="true">
                          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
                          </svg>
                        </span>
                        <h3 className="repo-card-title">{repoItem.repo}</h3>
                      </div>

                      <div className="repo-card-stats">
                        <span className="repo-stat-badge">
                          <strong>{repoItem.totalChunks}</strong> chunk{repoItem.totalChunks === 1 ? '' : 's'}
                        </span>
                        {typeof codeCount === 'number' && (
                          <span className="repo-stat-badge">
                            <strong>{codeCount}</strong> file{codeCount === 1 ? '' : 's'}
                          </span>
                        )}
                      </div>

                      {repoItem.ingestedAt && (
                        <div className="repo-card-meta">
                          <span>Indexed {formatRelativeTime(repoItem.ingestedAt)}</span>
                          {repoItem.headCommitSha && (
                            <span className="repo-sha" title={repoItem.headCommitSha}>
                              <code>{repoItem.headCommitSha.slice(0, 7)}</code>
                            </span>
                          )}
                        </div>
                      )}
                    </div>

                    <div className="repo-card-footer">
                      <button
                        type="button"
                        className="ask-repo-btn"
                        onClick={() => handleAskRepo(repoItem.repo)}
                        data-testid={`ask-repo-${repoItem.repo}`}
                        title={`Ask questions about ${repoItem.repo}`}
                      >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                          <circle cx="12" cy="12" r="10" />
                          <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
                          <line x1="12" y1="17" x2="12.01" y2="17" />
                        </svg>
                        <span>Ask</span>
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      </main>
    </div>
  );
};
