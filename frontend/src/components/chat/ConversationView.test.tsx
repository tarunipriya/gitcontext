import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConversationView } from './ConversationView';
import type { Chat } from '../../types/chat';

const mockSources = [
  {
    number: 1,
    path: 'backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/VectorStore.java',
    startLine: 45,
    endLine: 80,
    chunkType: 'CLASS',
    symbolName: 'VectorStore',
    url: 'https://github.com/tarunipriya/gitcontext/blob/main/backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/VectorStore.java#L45-L80',
    cited: true,
    snippet: 'public class VectorStore {\n  public void search() {\n    // dense vector search\n  }\n}',
  },
  {
    number: 2,
    path: 'backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/qdrant/QdrantClient.java',
    startLine: 20,
    endLine: 50,
    chunkType: 'CLASS',
    symbolName: 'QdrantClient',
    url: 'https://github.com/tarunipriya/gitcontext/blob/main/backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/qdrant/QdrantClient.java#L20-L50',
    cited: true,
    snippet: 'public class QdrantClient {\n  // client\n}',
  },
];

const mockChat: Chat = {
  id: 'chat-test-1',
  title: 'How is vector search implemented?',
  repo: 'tarunipriya/gitcontext',
  createdAt: '2026-10-31T10:00:00Z',
  updatedAt: '2026-10-31T10:01:00Z',
  messages: [
    {
      id: 'msg-1',
      role: 'user',
      content: 'How is vector search implemented?',
      createdAt: '2026-10-31T10:00:00Z',
    },
    {
      id: 'msg-2',
      role: 'assistant',
      content: 'Vector search is implemented in `VectorStore.java` [1] which communicates with Qdrant [2]. Also check [https://safe.github.com](https://safe.github.com) vs [javascript:alert(1)](javascript:alert(1)).\n\n```java\nVectorStore store = new VectorStore();\n```\n\n<script>window.XSS_EXECUTED = true;</script><img src="x" onerror="window.XSS_EXECUTED=true" />',
      createdAt: '2026-10-31T10:01:00Z',
      revealed: true,
      response: {
        question: 'How is vector search implemented?',
        answer: 'Vector search is implemented in `VectorStore.java` [1] which communicates with Qdrant [2]. Also check [https://safe.github.com](https://safe.github.com) vs [javascript:alert(1)](javascript:alert(1)).\n\n```java\nVectorStore store = new VectorStore();\n```\n\n<script>window.XSS_EXECUTED = true;</script><img src="x" onerror="window.XSS_EXECUTED=true" />',
        citations: [
          {
            number: 1,
            path: 'backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/VectorStore.java',
            startLine: 45,
            endLine: 80,
            symbolName: 'VectorStore',
            url: 'https://github.com/tarunipriya/gitcontext/blob/main/backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/VectorStore.java#L45-L80',
            chunkId: 'chunk-1',
          },
        ],
        sources: mockSources,
        model: 'gemini-3.5-flash',
        promptTokens: 1200,
        outputTokens: 350,
        retrievalMillis: 45,
        generationMillis: 820,
        totalMillis: 865,
        warnings: [],
      },
    },
  ],
};

describe('ConversationView Component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    (window as unknown as { XSS_EXECUTED?: boolean }).XSS_EXECUTED = false;
  });

  it('renders user message and assistant answer prose with CodeBlock', () => {
    render(
      <ConversationView
        chat={mockChat}
        onSendMessage={vi.fn()}
        onRetry={vi.fn()}
        onStop={vi.fn()}
        isPending={false}
        selectedRepo="tarunipriya/gitcontext"
        onRepoChange={vi.fn()}
        repos={[]}
      />
    );

    expect(screen.getByTestId('user-message')).toHaveTextContent('How is vector search implemented?');
    expect(screen.getByTestId('assistant-message')).toBeInTheDocument();
    expect(screen.getByText(/gemini-3.5-flash · 45 ms · 820 ms/i)).toBeInTheDocument();
  });

  it('answer renders markdown safely without executing raw HTML or scripts (XSS prevention)', () => {
    render(
      <ConversationView
        chat={mockChat}
        onSendMessage={vi.fn()}
        onRetry={vi.fn()}
        onStop={vi.fn()}
        isPending={false}
        selectedRepo="tarunipriya/gitcontext"
        onRepoChange={vi.fn()}
        repos={[]}
      />
    );

    // Script tags or onerror handlers should not execute
    expect((window as unknown as { XSS_EXECUTED?: boolean }).XSS_EXECUTED).toBe(false);
    expect(document.querySelector('script[src]')).toBeNull();
    // Raw script tag is not inserted as HTML script node
    const markdownContainer = screen.getByTestId('markdown-answer');
    expect(markdownContainer.querySelector('script')).toBeNull();
  });

  it('rejects unsafe protocols (javascript:) while rendering safe https links', () => {
    render(
      <ConversationView
        chat={mockChat}
        onSendMessage={vi.fn()}
        onRetry={vi.fn()}
        onStop={vi.fn()}
        isPending={false}
        selectedRepo="tarunipriya/gitcontext"
        onRepoChange={vi.fn()}
        repos={[]}
      />
    );

    // Safe link should render as anchor
    const safeLink = screen.getByRole('link', { name: /https:\/\/safe\.github\.com/i });
    expect(safeLink).toHaveAttribute('href', 'https://safe.github.com/');
    expect(safeLink).toHaveAttribute('target', '_blank');

    // Unsafe javascript: link should NOT be an anchor link
    const unsafeLink = screen.queryByRole('link', { name: /javascript:alert/i });
    expect(unsafeLink).toBeNull();
    expect(screen.getByText(/javascript:alert\(1\)/i)).toBeInTheDocument();
  });

  it('citation chip [1] opens right-side source panel Sheet with correct path and lines', async () => {
    const user = userEvent.setup();
    render(
      <ConversationView
        chat={mockChat}
        onSendMessage={vi.fn()}
        onRetry={vi.fn()}
        onStop={vi.fn()}
        isPending={false}
        selectedRepo="tarunipriya/gitcontext"
        onRepoChange={vi.fn()}
        repos={[]}
      />
    );

    // Click citation chip [1]
    const chip1 = screen.getByTestId('citation-chip-1');
    expect(chip1).toBeInTheDocument();
    await user.click(chip1);

    // Expect Sheet to be open
    await waitFor(() => {
      expect(screen.getByTestId('source-panel')).toBeInTheDocument();
    });

    expect(screen.getByTestId('source-panel-path')).toHaveTextContent(
      'backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/VectorStore.java'
    );
    expect(screen.getByTestId('source-panel-lines')).toHaveTextContent('Lines: L45-80');
    expect(screen.getByTestId('source-panel-github-link')).toBeInTheDocument();
  });

  it('clicking a source card opens the right-side source panel Sheet', async () => {
    const user = userEvent.setup();
    render(
      <ConversationView
        chat={mockChat}
        onSendMessage={vi.fn()}
        onRetry={vi.fn()}
        onStop={vi.fn()}
        isPending={false}
        selectedRepo="tarunipriya/gitcontext"
        onRepoChange={vi.fn()}
        repos={[]}
      />
    );

    const card2 = screen.getByTestId('source-card-2');
    await user.click(card2);

    await waitFor(() => {
      expect(screen.getByTestId('source-panel')).toBeInTheDocument();
    });

    expect(screen.getByTestId('source-panel-path')).toHaveTextContent(
      'backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/qdrant/QdrantClient.java'
    );
    expect(screen.getByTestId('source-panel-lines')).toHaveTextContent('Lines: L20-50');
  });

  it('copy answer button writes answer markdown to clipboard', async () => {
    const user = userEvent.setup();
    const clipboardSpy = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: clipboardSpy },
      configurable: true,
      writable: true,
    });

    render(
      <ConversationView
        chat={mockChat}
        onSendMessage={vi.fn()}
        onRetry={vi.fn()}
        onStop={vi.fn()}
        isPending={false}
        selectedRepo="tarunipriya/gitcontext"
        onRepoChange={vi.fn()}
        repos={[]}
      />
    );

    const copyBtn = screen.getByRole('button', { name: /copy answer/i });
    await user.click(copyBtn);

    expect(clipboardSpy).toHaveBeenCalledWith(mockChat.messages[1].response?.answer);
    expect(screen.getByText('Copied')).toBeInTheDocument();
  });

  it('retry button calls onRetry handler', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();

    render(
      <ConversationView
        chat={mockChat}
        onSendMessage={vi.fn()}
        onRetry={onRetry}
        onStop={vi.fn()}
        isPending={false}
        selectedRepo="tarunipriya/gitcontext"
        onRepoChange={vi.fn()}
        repos={[]}
      />
    );

    const retryBtn = screen.getByRole('button', { name: /retry answer/i });
    await user.click(retryBtn);

    expect(onRetry).toHaveBeenCalledWith('msg-2');
  });

  it('renders inline ProblemDetail error and triggers retry', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();

    const errorChat: Chat = {
      id: 'chat-err',
      title: 'Failed query',
      repo: 'tarunipriya/gitcontext',
      createdAt: '2026-10-31T10:00:00Z',
      updatedAt: '2026-10-31T10:01:00Z',
      messages: [
        { id: 'm-1', role: 'user', content: 'What is this?', createdAt: '2026-10-31T10:00:00Z' },
        {
          id: 'm-2',
          role: 'assistant',
          content: '',
          createdAt: '2026-10-31T10:00:05Z',
          error: {
            title: 'Qdrant Connection Failed',
            detail: 'Unable to reach vector database at localhost:6334',
            status: 503,
          },
        },
      ],
    };

    render(
      <ConversationView
        chat={errorChat}
        onSendMessage={vi.fn()}
        onRetry={onRetry}
        onStop={vi.fn()}
        isPending={false}
        selectedRepo="tarunipriya/gitcontext"
        onRepoChange={vi.fn()}
        repos={[]}
      />
    );

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Qdrant Connection Failed')).toBeInTheDocument();
    expect(screen.getByText('HTTP 503')).toBeInTheDocument();
    expect(screen.getByText(/Unable to reach vector database/i)).toBeInTheDocument();

    const retryBtn = screen.getByRole('button', { name: /retry/i });
    await user.click(retryBtn);
    expect(onRetry).toHaveBeenCalledWith('m-2');
  });
});
