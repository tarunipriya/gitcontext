import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from './App';
import { parseHash } from './utils/router';
import type { RepoSummaryResponse, AskResponse } from './api/client';
import { CHATS_STORAGE_KEY } from './lib/storage';

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

const mockAskResponse: AskResponse = {
  question: 'Where is the core indexing and chunking logic located?',
  answer: 'Core chunking is in `LineWindowChunker.java` [1] and indexing in `VectorStore.java` [2].',
  citations: [
    {
      number: 1,
      path: 'backend/src/main/java/io/github/tarunipriya/gitcontext/chunking/LineWindowChunker.java',
      startLine: 10,
      endLine: 40,
      symbolName: 'LineWindowChunker',
      url: 'https://github.com/spring-projects/spring-petclinic/blob/main/LineWindowChunker.java#L10-L40',
      chunkId: 'chunk-1',
    },
  ],
  sources: [
    {
      number: 1,
      path: 'backend/src/main/java/io/github/tarunipriya/gitcontext/chunking/LineWindowChunker.java',
      startLine: 10,
      endLine: 40,
      chunkType: 'CLASS',
      symbolName: 'LineWindowChunker',
      url: 'https://github.com/spring-projects/spring-petclinic/blob/main/LineWindowChunker.java#L10-L40',
      cited: true,
      snippet: 'public class LineWindowChunker { ... }',
    },
  ],
  model: 'gemini-3.5-flash',
  promptTokens: 800,
  outputTokens: 200,
  retrievalMillis: 30,
  generationMillis: 400,
  totalMillis: 430,
  warnings: [],
};

describe('App Component and Navigation', () => {
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
      if (url.includes('/ask')) {
        return Promise.resolve(
          new Response(JSON.stringify(mockAskResponse), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.reject(new Error(`Unhandled URL: ${url}`));
    });
  });

  it('correctly parses hash paths, conversation IDs, and query parameters', () => {
    expect(parseHash('')).toEqual({ route: 'start', repo: undefined });
    expect(parseHash('#')).toEqual({ route: 'start', repo: undefined });
    expect(parseHash('#/')).toEqual({ route: 'start', repo: undefined });
    expect(parseHash('#/repos')).toEqual({ route: 'repos', repo: undefined });
    expect(parseHash('#/styleguide')).toEqual({ route: 'styleguide', repo: undefined });
    expect(parseHash('#/c/chat-123')).toEqual({ route: 'chat', chatId: 'chat-123', repo: undefined });
    expect(parseHash('#/?repo=owner/repo')).toEqual({ route: 'start', repo: 'owner/repo' });
    expect(parseHash('#/repos?repo=owner/repo')).toEqual({ route: 'repos', repo: 'owner/repo' });
  });

  it('renders start screen by default with heading and composer', async () => {
    render(<App />);

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: /what do you want to know about this repo\?/i })
      ).toBeInTheDocument();
    });

    expect(screen.getByPlaceholderText(/ask anything about the codebase/i)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /new chat/i })[0]).toBeInTheDocument();
  });

  it('example chip starts a chat, calls ask API, and renders assistant answer', async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('example-chip-1')).toBeInTheDocument();
    });

    const exampleChip = screen.getByTestId('example-chip-1');
    expect(exampleChip).toHaveTextContent('Where is the core indexing and chunking logic located?');

    await user.click(exampleChip);

    // Verify ask API was called
    await waitFor(() => {
      const askCall = fetchSpy.mock.calls.find((call) => String(call[0]).includes('/ask'));
      expect(askCall).toBeDefined();
    });

    // Hash should transition to #/c/<chatId>
    await waitFor(() => {
      expect(window.location.hash).toMatch(/^#\/c\//);
    });

    // Assistant response displayed
    await waitFor(() => {
      expect(screen.getByTestId('assistant-message')).toBeInTheDocument();
    });
  });

  it('Enter key sends question, Shift+Enter creates newline, empty composer cannot send', async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    render(<App />);

    await waitFor(() => {
      expect(screen.getByPlaceholderText(/ask anything about the codebase/i)).toBeInTheDocument();
    });

    const textarea = screen.getByPlaceholderText(/ask anything about the codebase/i);
    const sendBtn = screen.getByRole('button', { name: /send message/i });

    // Empty composer: send button is disabled
    expect(sendBtn).toBeDisabled();

    // Type spaces: still disabled
    await user.type(textarea, '   ');
    expect(sendBtn).toBeDisabled();

    // Shift+Enter should NOT send
    await user.clear(textarea);
    await user.type(textarea, 'Line 1{Shift>}{Enter}{/Shift}Line 2');
    expect(fetchSpy.mock.calls.filter((c) => String(c[0]).includes('/ask'))).toHaveLength(0);

    // Enter alone should send
    await user.type(textarea, '{Enter}');

    await waitFor(() => {
      const askCall = fetchSpy.mock.calls.find((c) => String(c[0]).includes('/ask'));
      expect(askCall).toBeDefined();
    });
  });

  it('Stop button aborts the in-flight ask request', async () => {
    const user = userEvent.setup();

    let abortSignalTriggered = false;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes('/ask')) {
        init?.signal?.addEventListener('abort', () => {
          abortSignalTriggered = true;
        });
        return new Promise<Response>(() => {
          // Never resolves
        });
      }
      if (url.includes('/api/repos')) {
        return Promise.resolve(
          new Response(JSON.stringify(mockRepos), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        );
      }
      return Promise.resolve(new Response(JSON.stringify([])));
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByPlaceholderText(/ask anything about the codebase/i)).toBeInTheDocument();
    });

    const textarea = screen.getByPlaceholderText(/ask anything about the codebase/i);
    await user.type(textarea, 'Tell me about the architecture');
    const sendBtn = screen.getByRole('button', { name: /send message/i });
    await user.click(sendBtn);

    // Stop button appears
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /stop generating/i })).toBeInTheDocument();
    });

    const stopBtn = screen.getByRole('button', { name: /stop generating/i });
    await user.click(stopBtn);

    expect(abortSignalTriggered).toBe(true);
  });

  it('persists chats to localStorage, displays them under Today in sidebar, and delete removes chat', async () => {
    const user = userEvent.setup();

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('example-chip-0')).toBeInTheDocument();
    });

    // Start a chat
    await user.click(screen.getByTestId('example-chip-0'));

    await waitFor(() => {
      expect(screen.getByTestId('assistant-message')).toBeInTheDocument();
    });

    // Verify localStorage has persisted chat
    const stored = JSON.parse(localStorage.getItem(CHATS_STORAGE_KEY) || '[]');
    expect(stored.length).toBeGreaterThan(0);
    expect(stored[0].title).toBeDefined();

    // Verify it appears in sidebar under Today group
    expect(screen.getByText('Today')).toBeInTheDocument();
    expect(screen.getByText(stored[0].title)).toBeInTheDocument();

    // Click options menu on chat item to delete
    const optionsBtn = screen.getByRole('button', { name: new RegExp(`Options for ${stored[0].title}`, 'i') });
    await user.click(optionsBtn);

    const deleteBtn = await screen.findByRole('menuitem', { name: /delete/i });
    await user.click(deleteBtn);

    // Chat is removed from localStorage and sidebar
    await waitFor(() => {
      const remaining = JSON.parse(localStorage.getItem(CHATS_STORAGE_KEY) || '[]');
      expect(remaining).toHaveLength(0);
    });
  });

  it('switches to Repositories page and back via sidebar and hash routing', async () => {
    const user = userEvent.setup();
    render(<App />);

    // Click Repositories in sidebar
    const reposBtn = screen.getByRole('button', { name: 'Repositories' });
    await user.click(reposBtn);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /ingest repository/i })).toBeInTheDocument();
    });
    expect(window.location.hash).toContain('#/repos');

    // Click New chat to navigate back
    const newChatBtn = screen.getAllByRole('button', { name: /new chat/i })[0];
    await user.click(newChatBtn);

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: /what do you want to know about this repo\?/i })
      ).toBeInTheDocument();
    });
  });

  it('pre-fills repository on Start screen when navigated from Repositories page New chat button', async () => {
    window.location.hash = '#/repos';
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('spring-projects/spring-petclinic')).toBeInTheDocument();
    });

    const askBtn = screen.getByTestId('ask-repo-spring-projects/spring-petclinic');
    await user.click(askBtn);

    act(() => {
      window.dispatchEvent(new HashChangeEvent('hashchange'));
    });

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: /what do you want to know about this repo\?/i })
      ).toBeInTheDocument();
    });

    const repoPicker = screen.getByTestId('repo-picker') as HTMLSelectElement;
    expect(repoPicker.value).toBe('spring-projects/spring-petclinic');
  });
});
