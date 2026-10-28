import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from './App';
import { parseHash } from './utils/router';
import type { RepoSummaryResponse } from './api/client';

const mockRepos: RepoSummaryResponse[] = [
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

describe('App Component and Hash Navigation', () => {
  beforeEach(() => {
    window.location.hash = '';
    localStorage.clear();
    vi.restoreAllMocks();

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
  });

  it('correctly parses hash paths and query parameters', () => {
    expect(parseHash('')).toEqual({ route: 'ask', repo: undefined });
    expect(parseHash('#')).toEqual({ route: 'ask', repo: undefined });
    expect(parseHash('#/ask')).toEqual({ route: 'ask', repo: undefined });
    expect(parseHash('#/repos')).toEqual({ route: 'repos', repo: undefined });
    expect(parseHash('#/ask?repo=owner/repo')).toEqual({ route: 'ask', repo: 'owner/repo' });
    expect(parseHash('#/repos?repo=owner/repo')).toEqual({ route: 'repos', repo: 'owner/repo' });
  });

  it('renders Ask page by default and marks Ask navigation tab with aria-current="page"', () => {
    render(<App />);

    const askTab = screen.getByRole('link', { name: /ask/i });
    const reposTab = screen.getByRole('link', { name: /repositories/i });

    expect(askTab).toHaveAttribute('aria-current', 'page');
    expect(reposTab).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('button', { name: /ask gitcontext/i })).toBeInTheDocument();
  });

  it('switches to Repositories page on hashchange event and updates aria-current', async () => {
    render(<App />);

    // Change hash to #/repos
    act(() => {
      window.location.hash = '#/repos';
      window.dispatchEvent(new HashChangeEvent('hashchange'));
    });

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /ingest repository/i })).toBeInTheDocument();
    });

    expect(screen.getByRole('link', { name: /repositories/i })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: /ask/i })).not.toHaveAttribute('aria-current');
  });

  it('switches back to Ask page when clicking the Ask nav link', async () => {
    window.location.hash = '#/repos';
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /ingest repository/i })).toBeInTheDocument();
    });

    const askTab = screen.getByRole('link', { name: /ask/i });
    await user.click(askTab);

    // In JSDOM, clicking anchor tags may not fire hashchange automatically, so dispatch it
    act(() => {
      window.location.hash = '#/ask';
      window.dispatchEvent(new HashChangeEvent('hashchange'));
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /ask gitcontext/i })).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: /ask/i })).toHaveAttribute('aria-current', 'page');
  });

  it('pre-fills repository input in Ask page from URL hash parameter', async () => {
    window.location.hash = '#/ask?repo=custom-org/custom-repo';
    render(<App />);

    const repoInput = screen.getByLabelText(/repository/i) as HTMLInputElement;
    expect(repoInput.value).toBe('custom-org/custom-repo');
  });

  it('navigates from Repositories page to Ask page with pre-filled repo when Ask button is clicked', async () => {
    window.location.hash = '#/repos';
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('spring-projects/spring-petclinic')).toBeInTheDocument();
    });

    const askBtn = screen.getByTestId('ask-repo-spring-projects/spring-petclinic');
    await user.click(askBtn);

    // Hash change triggered
    act(() => {
      window.dispatchEvent(new HashChangeEvent('hashchange'));
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /ask gitcontext/i })).toBeInTheDocument();
    });

    const repoInput = screen.getByLabelText(/repository/i) as HTMLInputElement;
    expect(repoInput.value).toBe('spring-projects/spring-petclinic');
  });
});
