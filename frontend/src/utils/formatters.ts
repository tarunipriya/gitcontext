import type { CitationResponse, SourceResponse } from '../api/client';

/**
 * Matches valid repository references in "owner/name" or full GitHub URL format,
 * identical to the backend io.github.tarunipriya.gitcontext.model.RepoRef pattern.
 */
export const REPO_REF_REGEX = /^(?:(?:https?:\/\/|git@)?github\.com[/:]|\/)?([a-zA-Z0-9_.-]+)\/([a-zA-Z0-9_.-]+?)(?:\.git)?\/?$/;

export interface ParsedRepoRef {
  owner: string;
  name: string;
  fullName: string;
}

/**
 * Parses and validates a repository string into its owner and repository name components.
 */
export function parseRepoRef(input: string): ParsedRepoRef | null {
  if (!input || !input.trim()) {
    return null;
  }
  const match = input.trim().match(REPO_REF_REGEX);
  if (!match) {
    return null;
  }
  const owner = match[1];
  const name = match[2];
  if (!owner || !name) {
    return null;
  }
  return {
    owner,
    name,
    fullName: `${owner}/${name}`,
  };
}

/**
 * Returns true if the input string is a valid GitHub repository reference.
 */
export function isValidRepoRef(input: string): boolean {
  return parseRepoRef(input) !== null;
}

/**
 * Formats a readable title for sources and citations.
 * Sources with a file path return the relative path.
 * Sources without a file path (COMMIT, PULL_REQUEST, ISSUE) return:
 * - commits: "commit abc1234 — <first line of message>"
 * - PRs: "PR #16 — <title>"
 * - issues: "Issue #20 — <title>"
 */
export function formatSourceTitle(source: SourceResponse | CitationResponse | {
  path?: string | null;
  title?: string | null;
  url?: string | null;
  chunkType?: string | null;
  sourceType?: string | null;
  commitSha?: string | null;
}): string {
  if (!source) {
    return 'Unknown source';
  }

  // If a valid file path is present, return it
  if (source.path && source.path.trim().length > 0) {
    return source.path;
  }

  const chunkType = (source as SourceResponse).chunkType || (source as { sourceType?: string | null }).sourceType || '';
  const url = source.url || '';
  const rawTitle = source.title ? source.title.trim() : '';

  // Check if title is already fully formatted
  if (rawTitle.startsWith('commit ') || rawTitle.startsWith('PR #') || rawTitle.startsWith('Issue #')) {
    return rawTitle;
  }

  const isCommit = chunkType.toUpperCase() === 'COMMIT' || url.includes('/commit/');
  const isPr = chunkType.toUpperCase() === 'PULL_REQUEST' || url.includes('/pull/');
  const isIssue = chunkType.toUpperCase() === 'ISSUE' || url.includes('/issues/');

  if (isCommit) {
    const commitSha = (source as SourceResponse).commitSha;
    let sha = '';
    if (commitSha && commitSha.trim().length >= 7) {
      sha = commitSha.trim().slice(0, 7);
    } else {
      const match = url.match(/\/commit\/([a-fA-F0-9]{7})/);
      if (match) {
        sha = match[1];
      }
    }

    if (sha && rawTitle) {
      return `commit ${sha} — ${rawTitle}`;
    }
    if (sha) {
      return `commit ${sha}`;
    }
    if (rawTitle) {
      return `commit — ${rawTitle}`;
    }
    return 'commit';
  }

  if (isPr) {
    const match = url.match(/\/pull\/(\d+)/);
    const prNum = match ? match[1] : null;

    if (prNum && rawTitle) {
      return `PR #${prNum} — ${rawTitle}`;
    }
    if (prNum) {
      return `PR #${prNum}`;
    }
    if (rawTitle) {
      return `PR — ${rawTitle}`;
    }
    return 'Pull Request';
  }

  if (isIssue) {
    const match = url.match(/\/issues\/(\d+)/);
    const issueNum = match ? match[1] : null;

    if (issueNum && rawTitle) {
      return `Issue #${issueNum} — ${rawTitle}`;
    }
    if (issueNum) {
      return `Issue #${issueNum}`;
    }
    if (rawTitle) {
      return `Issue — ${rawTitle}`;
    }
    return 'Issue';
  }

  return rawTitle || 'Unknown source';
}

/**
 * Formats elapsed time between start and finish/now into human-readable duration (e.g. "12s" or "1m 05s").
 */
export function formatElapsedTime(startedAt?: string | null, finishedAt?: string | null, nowMs: number = Date.now()): string {
  if (!startedAt) {
    return '0s';
  }
  const start = new Date(startedAt).getTime();
  const end = finishedAt ? new Date(finishedAt).getTime() : nowMs;
  if (isNaN(start) || isNaN(end) || end < start) {
    return '0s';
  }
  const totalSeconds = Math.max(0, Math.floor((end - start) / 1000));
  if (totalSeconds < 60) {
    return `${totalSeconds}s`;
  }
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${seconds.toString().padStart(2, '0')}s`;
}

/**
 * Formats an ISO date string into a relative or formatted date string.
 */
export function formatRelativeTime(isoString?: string | null): string {
  if (!isoString) {
    return 'Never';
  }
  const date = new Date(isoString);
  const time = date.getTime();
  if (isNaN(time)) {
    return isoString;
  }
  const diffSec = Math.floor((Date.now() - time) / 1000);
  if (diffSec < 10) return 'just now';
  if (diffSec < 60) return `${diffSec}s ago`;
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}h ago`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) return `${diffDay}d ago`;

  return date.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

/**
 * Formats startLine and endLine into "L10" or "L10-25", or null if not specified.
 */
export function formatLineRange(start?: number | null, end?: number | null): string | null {
  if (start == null) return null;
  if (end == null || end === start) return `L${start}`;
  return `L${start}-${end}`;
}

