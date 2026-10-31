import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  FolderGit2,
  Plus,
  Clock,
  CheckCircle2,
  XCircle,
  Loader2,
  FileCode,
  Layers,
  Calendar,
  AlertCircle,
  GitBranch,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
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
import {
  parseRepoRef,
  isValidRepoRef,
  formatElapsedTime,
  formatRelativeTime,
} from '../utils/formatters';
import { cn } from '@/lib/utils';

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
        setReposError(
          err.problemDetail || {
            title: err.title || 'Error',
            detail: err.detail || err.message,
            status: err.status,
          }
        );
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

  // Polling effect: depends ONLY on hasActiveJob
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
        setSubmitError(
          err.problemDetail || {
            title: err.title || `Request failed (${err.status})`,
            detail: err.detail || err.message,
            status: err.status,
          }
        );
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

  const handleAskRepoClick = (repoName: string) => {
    if (onAskRepo) {
      onAskRepo(repoName);
    } else {
      window.location.hash = `#/?repo=${encodeURIComponent(repoName)}`;
    }
  };

  return (
    <div className="flex-1 overflow-y-auto px-4 py-8 bg-background">
      <main className="max-w-4xl w-full mx-auto space-y-8" aria-label="Repositories management">
        {/* Settings header */}
        <div className="border-b border-border/60 pb-5">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Repositories</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Manage connected GitHub repositories, view indexing jobs, and launch targeted conversations.
          </p>
        </div>

        {/* Ingest Repository Form Card */}
        <section className="rounded-2xl border border-border bg-card p-6 shadow-xs space-y-4" aria-labelledby="ingest-heading">
          <div>
            <h2 id="ingest-heading" className="text-base font-semibold text-foreground">
              Ingest Repository
            </h2>
            <p className="text-xs text-muted-foreground mt-0.5">
              Index any GitHub repository for hybrid retrieval, chunking, and grounded question answering.
            </p>
          </div>

          <form onSubmit={handleSubmit} noValidate className="space-y-3">
            <div>
              <label htmlFor="repo-ingest-input" className="block text-xs font-medium text-foreground mb-1.5">
                GitHub Repository
              </label>
              <div className="flex flex-col sm:flex-row gap-2">
                <input
                  id="repo-ingest-input"
                  type="text"
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
                  className="flex-1 rounded-xl border border-input bg-background px-3.5 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:border-transparent transition-all"
                />
                <Button
                  id="ingest-button"
                  type="submit"
                  disabled={submitting || repoInput.trim().length === 0}
                  className="rounded-xl px-5 text-xs font-medium gap-1.5 h-10 shrink-0"
                >
                  {submitting ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      <span>Submitting...</span>
                    </>
                  ) : (
                    <>
                      <Plus className="h-4 w-4" />
                      <span>Ingest</span>
                    </>
                  )}
                </Button>
              </div>
            </div>

            {submitError && (
              <div
                className="rounded-xl border border-destructive/40 bg-destructive/10 p-3.5 flex items-start gap-2.5 text-xs"
                role="alert"
                data-testid="ingest-error-banner"
              >
                <AlertCircle className="h-4 w-4 text-destructive shrink-0 mt-0.5" />
                <div className="flex-1 space-y-1">
                  <div className="flex items-center gap-2">
                    <strong className="text-destructive font-medium">
                      {submitError.title || 'Error'}
                    </strong>
                    {submitError.status && (
                      <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-destructive/20 text-destructive">
                        HTTP {submitError.status}
                      </span>
                    )}
                  </div>
                  {submitError.detail && (
                    <p className="text-muted-foreground leading-relaxed">{submitError.detail}</p>
                  )}
                </div>
              </div>
            )}
          </form>
        </section>

        {/* Active & Recent Ingestion Jobs */}
        {jobs.length > 0 && (
          <section className="space-y-3" aria-labelledby="jobs-heading">
            <h2 id="jobs-heading" className="text-sm font-semibold text-foreground uppercase tracking-wider">
              Ingestion Jobs ({jobs.length})
            </h2>

            <div className="space-y-2.5" role="list">
              {jobs.map((job) => {
                const elapsed = formatElapsedTime(
                  job.startedAt || job.submittedAt,
                  job.finishedAt,
                  currentTime
                );

                const isRunning = job.status === 'RUNNING';
                const isCompleted = job.status === 'COMPLETED';
                const isFailed = job.status === 'FAILED';
                const isQueued = job.status === 'QUEUED';

                return (
                  <article
                    key={job.jobId}
                    role="listitem"
                    className="rounded-xl border border-border/80 bg-card p-4 shadow-2xs space-y-2"
                  >
                    <div className="flex items-center justify-between gap-2 flex-wrap">
                      <div className="flex items-center gap-2.5 min-w-0">
                        <FolderGit2 className="h-4 w-4 text-muted-foreground shrink-0" />
                        <span className="font-mono text-sm font-semibold text-foreground truncate">
                          {job.repo}
                        </span>
                        <span className="font-mono text-xs text-muted-foreground flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          <span>⏱ {elapsed}</span>
                        </span>
                      </div>

                      <span
                        className={cn(
                          'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-mono font-medium',
                          isRunning && 'bg-primary/10 text-primary border border-primary/20',
                          isCompleted && 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20',
                          isFailed && 'bg-destructive/10 text-destructive border border-destructive/20',
                          isQueued && 'bg-amber-500/10 text-amber-500 border border-amber-500/20'
                        )}
                      >
                        {isRunning && <Loader2 className="h-3 w-3 animate-spin" />}
                        {isCompleted && <CheckCircle2 className="h-3 w-3" />}
                        {isFailed && <XCircle className="h-3 w-3" />}
                        <span>{job.status}</span>
                      </span>
                    </div>

                    {isFailed && job.errorMessage && (
                      <div className="text-xs text-destructive bg-destructive/10 border border-destructive/20 p-2.5 rounded-lg" role="alert">
                        <strong>Failure:</strong> {job.errorMessage}
                      </div>
                    )}

                    {isCompleted && job.summary && (
                      <div className="text-xs text-muted-foreground bg-muted/40 p-2.5 rounded-lg font-mono">
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
        <section className="space-y-4" aria-labelledby="indexed-repos-heading">
          <div>
            <h2 id="indexed-repos-heading" className="text-base font-semibold text-foreground">
              Indexed Repositories
            </h2>
            <p className="text-xs text-muted-foreground mt-0.5">
              Repositories ready for natural language grounded question answering.
            </p>
          </div>

          {reposLoading && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4" data-testid="repos-loading-skeleton">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="h-36 rounded-2xl border border-border bg-card/50 animate-pulse" />
              ))}
            </div>
          )}

          {!reposLoading && reposError && (
            <div className="rounded-xl border border-destructive/40 bg-destructive/10 p-4 text-xs" role="alert">
              <strong className="text-destructive font-semibold">
                {reposError.title || 'Error Loading Repositories'}
              </strong>
              {reposError.detail && <p className="text-muted-foreground mt-1">{reposError.detail}</p>}
            </div>
          )}

          {!reposLoading && !reposError && repos.length === 0 && (
            <div
              className="rounded-2xl border border-dashed border-border p-8 text-center space-y-3"
              data-testid="repos-empty-state"
            >
              <FolderGit2 className="h-8 w-8 text-muted-foreground/60 mx-auto" />
              <div>
                <h3 className="text-sm font-semibold text-foreground">No repositories indexed yet</h3>
                <p className="text-xs text-muted-foreground mt-1 max-w-sm mx-auto">
                  Enter a GitHub repository name above to ingest code files, PRs, and commits into the search index.
                </p>
              </div>
            </div>
          )}

          {!reposLoading && !reposError && repos.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4" data-testid="repos-grid">
              {repos.map((repoItem) => {
                const codeCount = repoItem.countsBySourceType?.CODE;
                return (
                  <article
                    key={repoItem.repo}
                    className="rounded-2xl border border-border bg-card p-5 shadow-2xs flex flex-col justify-between space-y-4"
                    data-testid={`repo-card-${repoItem.repo}`}
                  >
                    <div className="space-y-3">
                      <div className="flex items-center gap-2 min-w-0">
                        <FolderGit2 className="h-4 w-4 text-primary shrink-0" />
                        <h3 className="text-sm font-bold text-foreground truncate font-mono">
                          {repoItem.repo}
                        </h3>
                      </div>

                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="inline-flex items-center gap-1 text-xs font-mono text-muted-foreground bg-muted/60 px-2 py-0.5 rounded-md">
                          <Layers className="h-3 w-3" />
                          <span>{repoItem.totalChunks}</span> chunks
                        </span>
                        {typeof codeCount === 'number' && (
                          <span className="inline-flex items-center gap-1 text-xs font-mono text-muted-foreground bg-muted/60 px-2 py-0.5 rounded-md">
                            <FileCode className="h-3 w-3" />
                            <span>{codeCount}</span> files
                          </span>
                        )}
                      </div>

                      {repoItem.ingestedAt && (
                        <div className="flex items-center gap-2 text-[11px] text-muted-foreground pt-1 flex-wrap font-mono">
                          <span className="flex items-center gap-1">
                            <Calendar className="h-3 w-3" />
                            <span>Indexed {formatRelativeTime(repoItem.ingestedAt)}</span>
                          </span>
                          {repoItem.headCommitSha && (
                            <span className="flex items-center gap-1 text-foreground/80">
                              <GitBranch className="h-3 w-3" />
                              <code>{repoItem.headCommitSha.slice(0, 7)}</code>
                            </span>
                          )}
                        </div>
                      )}
                    </div>

                    <div className="pt-2 border-t border-border/40 flex justify-end">
                      <Button
                        type="button"
                        size="sm"
                        onClick={() => handleAskRepoClick(repoItem.repo)}
                        data-testid={`ask-repo-${repoItem.repo}`}
                        title={`New chat about ${repoItem.repo}`}
                        className="rounded-xl px-3.5 h-8 text-xs gap-1.5 font-medium"
                      >
                        <Plus className="h-3.5 w-3.5" />
                        <span>New chat</span>
                      </Button>
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
