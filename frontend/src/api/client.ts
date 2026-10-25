/**
 * ProblemDetail RFC 7807 representation matching Spring Boot's ProblemDetail.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  [key: string]: unknown;
}

/**
 * Request payload for repository question answering.
 * Corresponds to io.github.tarunipriya.gitcontext.api.dto.AskRequest.
 */
export interface AskRequest {
  question: string;
  mode?: 'HYBRID' | 'VECTOR' | 'KEYWORD' | string;
  excludeTests?: boolean;
  rerank?: boolean;
  maxSources?: number;
}

/**
 * Verified citation linking response text to an indexed repository chunk.
 * Corresponds to io.github.tarunipriya.gitcontext.api.dto.CitationResponse.
 */
export interface CitationResponse {
  number: number;
  path: string;
  startLine?: number | null;
  endLine?: number | null;
  symbolName?: string | null;
  url?: string | null;
  chunkId: string;
}

/**
 * Numbered source document formatted for context injection in API response.
 * Corresponds to io.github.tarunipriya.gitcontext.api.dto.SourceResponse.
 */
export interface SourceResponse {
  number: number;
  path: string;
  startLine?: number | null;
  endLine?: number | null;
  chunkType?: string | null;
  symbolName?: string | null;
  url?: string | null;
  cited: boolean;
  snippet: string;
}

/**
 * API response payload containing the generated answer, verified citations, and timings.
 * Corresponds to io.github.tarunipriya.gitcontext.api.dto.AskResponse.
 */
export interface AskResponse {
  question: string;
  answer: string;
  citations: CitationResponse[];
  sources: SourceResponse[];
  model: string;
  promptTokens: number;
  outputTokens: number;
  thoughtsTokens?: number | null;
  retrievalMillis: number;
  generationMillis: number;
  totalMillis: number;
  warnings?: string[] | null;
}

/**
 * Lifecycle status of an ingestion job.
 * Corresponds to io.github.tarunipriya.gitcontext.ingestion.JobStatus.
 */
export type JobStatus = 'SUBMITTED' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';

/**
 * Response payload representing an ingestion job's status and metadata.
 * Corresponds to io.github.tarunipriya.gitcontext.api.JobResponse.
 */
export interface JobResponse {
  jobId: string;
  repo: string;
  status: JobStatus;
  submittedAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  summary?: string | null;
  errorMessage?: string | null;
}

/**
 * Diagnostic summary response for the latest completed repository ingestion.
 * Corresponds to io.github.tarunipriya.gitcontext.api.RepoSummaryResponse.
 */
export interface RepoSummaryResponse {
  repo: string;
  headCommitSha: string;
  countsBySourceType: Record<string, number>;
  totalChunks: number;
  chunkCountsByType: Record<string, number>;
  avgChunkTokens: number;
  maxChunkTokens: number;
  indexedPoints: number;
  embedMillis: number;
  upsertMillis: number;
  ingestedAt: string;
  warnings?: string[];
}

/**
 * Typed error thrown on non-2xx responses from the GitContext API,
 * extracting RFC 7807 ProblemDetail fields (status, title, detail).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly title?: string;
  readonly detail?: string;
  readonly problemDetail?: ProblemDetail;

  constructor(status: number, message: string, problemDetail?: ProblemDetail) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.title = problemDetail?.title;
    this.detail = problemDetail?.detail;
    this.problemDetail = problemDetail;
    Object.setPrototypeOf(this, ApiError.prototype);
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let problemDetail: ProblemDetail | undefined;
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && (contentType.includes('application/json') || contentType.includes('application/problem+json'))) {
        problemDetail = (await response.json()) as ProblemDetail;
      }
    } catch {
      // In case body cannot be parsed as JSON
    }

    const message = problemDetail?.detail
      || problemDetail?.title
      || `Request failed with HTTP ${response.status}`;

    throw new ApiError(response.status, message, problemDetail);
  }

  return (await response.json()) as T;
}

/**
 * Sends a natural language question about an indexed repository to the GitContext /ask endpoint.
 */
export async function askRepo(
  owner: string,
  name: string,
  requestPayload: AskRequest,
  signal?: AbortSignal
): Promise<AskResponse> {
  const url = `/api/repos/${encodeURIComponent(owner)}/${encodeURIComponent(name)}/ask`;
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(requestPayload),
    signal,
  });

  return handleResponse<AskResponse>(response);
}

/**
 * Submits an ingestion job for the specified repository.
 */
export async function submitIngestion(repo: string, signal?: AbortSignal): Promise<JobResponse> {
  const response = await fetch('/api/ingestions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify({ repo }),
    signal,
  });
  return handleResponse<JobResponse>(response);
}

/**
 * Retrieves the status and metadata for a specific ingestion job.
 */
export async function getJob(jobId: string, signal?: AbortSignal): Promise<JobResponse> {
  const response = await fetch(`/api/ingestions/${encodeURIComponent(jobId)}`, {
    headers: { 'Accept': 'application/json' },
    signal,
  });
  return handleResponse<JobResponse>(response);
}

/**
 * Retrieves recent ingestion jobs ordered newest first.
 */
export async function getRecentJobs(signal?: AbortSignal): Promise<JobResponse[]> {
  const response = await fetch('/api/ingestions', {
    headers: { 'Accept': 'application/json' },
    signal,
  });
  return handleResponse<JobResponse[]>(response);
}

/**
 * Retrieves diagnostic summary statistics for the latest completed ingestion of a repository.
 */
export async function getRepoSummary(owner: string, name: string, signal?: AbortSignal): Promise<RepoSummaryResponse> {
  const response = await fetch(`/api/repos/${encodeURIComponent(owner)}/${encodeURIComponent(name)}/summary`, {
    headers: { 'Accept': 'application/json' },
    signal,
  });
  return handleResponse<RepoSummaryResponse>(response);
}
