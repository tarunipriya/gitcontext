import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AskPage } from './AskPage';
import type { AskResponse } from '../api/client';

const mockSuccessResponse: AskResponse = {
  question: 'How is vector search implemented?',
  answer: 'Vector search is implemented in `VectorStore.java` [1] which communicates with Qdrant [2]. Also note invalid [99].',
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
    {
      number: 2,
      path: 'backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/qdrant/QdrantClient.java',
      startLine: 20,
      endLine: 50,
      symbolName: 'QdrantClient',
      url: 'https://github.com/tarunipriya/gitcontext/blob/main/backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/qdrant/QdrantClient.java#L20-L50',
      chunkId: 'chunk-2',
    },
  ],
  sources: [
    {
      number: 1,
      path: 'backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/VectorStore.java',
      startLine: 45,
      endLine: 80,
      chunkType: 'CLASS',
      symbolName: 'VectorStore',
      url: 'https://github.com/tarunipriya/gitcontext/blob/main/backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/VectorStore.java#L45-L80',
      cited: true,
      snippet: 'public class VectorStore { ... }',
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
      snippet: 'public class QdrantClient { ... }',
    },
    {
      number: 3,
      path: 'backend/src/main/java/io/github/tarunipriya/gitcontext/retrieval/SearchMode.java',
      startLine: 1,
      endLine: 15,
      chunkType: 'ENUM',
      symbolName: 'SearchMode',
      url: 'https://github.com/tarunipriya/gitcontext/blob/main/backend/src/main/java/io/github/tarunipriya/gitcontext/retrieval/SearchMode.java#L1-L15',
      cited: false,
      snippet: 'public enum SearchMode { VECTOR, KEYWORD, HYBRID }',
    },
  ],
  model: 'gemini-3.5-flash',
  promptTokens: 1250,
  outputTokens: 340,
  retrievalMillis: 65,
  generationMillis: 1420,
  totalMillis: 1485,
  warnings: [],
};

describe('AskPage Component', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders with default repository and remembers changes in localStorage', async () => {
    const user = userEvent.setup();
    render(<AskPage />);

    const repoInput = screen.getByLabelText(/repository/i) as HTMLInputElement;
    expect(repoInput.value).toBe('tarunipriya/gitcontext');

    await user.clear(repoInput);
    await user.type(repoInput, 'spring-projects/spring-petclinic');
    expect(repoInput.value).toBe('spring-projects/spring-petclinic');
    expect(localStorage.getItem('gitcontext:last_repo')).toBe('spring-projects/spring-petclinic');
  });

  it('executes full Ask flow with mocked fetch (loading -> answer -> sources -> telemetry)', async () => {
    const user = userEvent.setup();

    let resolveFetch!: (res: Response) => void;
    const fetchPromise = new Promise<Response>((resolve) => {
      resolveFetch = resolve;
    });

    vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() => fetchPromise);

    render(<AskPage />);

    const textarea = screen.getByLabelText(/question/i);
    const askButton = screen.getByRole('button', { name: /ask gitcontext/i });

    expect(askButton).toBeDisabled();

    await user.type(textarea, 'How is vector search implemented?');
    expect(askButton).toBeEnabled();

    await user.click(askButton);

    // 1. Verify loading state while fetch is in-flight
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText(/retrieving repository knowledge/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /searching & generating/i })).toBeDisabled();

    // 2. Resolve fetch response
    resolveFetch(
      new Response(JSON.stringify(mockSuccessResponse), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    );

    // 3. Response renders and loading disappears
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Answer' })).toBeInTheDocument();
    });

    expect(screen.queryByRole('status')).toBeNull();

    // Verify answer content
    expect(screen.getByText(/Vector search is implemented in/i)).toBeInTheDocument();

    // Verify sources panel
    expect(screen.getByRole('heading', { name: /Retrieved Sources \(3\)/i })).toBeInTheDocument();
    expect(screen.getByText('backend/src/main/java/io/github/tarunipriya/gitcontext/indexing/VectorStore.java')).toBeInTheDocument();
    expect(screen.getByText('L45-80')).toBeInTheDocument();
    expect(screen.getAllByText('cited')).toHaveLength(2); // 2 cited sources

    // Verify telemetry footer
    expect(screen.getByText('gemini-3.5-flash')).toBeInTheDocument();
    expect(screen.getByText('65 ms')).toBeInTheDocument();
    expect(screen.getByText('1420 ms')).toBeInTheDocument();
    expect(screen.getByText('1485 ms')).toBeInTheDocument();
    expect(screen.getByText('1250 in / 340 out')).toBeInTheDocument();
  });

  it('submits on Ctrl+Enter keyboard shortcut', async () => {
    const user = userEvent.setup();

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() =>
      Promise.resolve(
        new Response(JSON.stringify(mockSuccessResponse), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      )
    );

    render(<AskPage />);

    const textarea = screen.getByLabelText(/question/i);
    await user.type(textarea, 'Shortcut question{Control>}{Enter}{/Control}');

    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledTimes(1);
    });
  });

  it('transforms valid citation markers [n] into external links and keeps invalid numbers as plain text', async () => {
    const user = userEvent.setup();

    vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() =>
      Promise.resolve(
        new Response(JSON.stringify(mockSuccessResponse), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      )
    );

    render(<AskPage />);

    const textarea = screen.getByLabelText(/question/i);
    await user.type(textarea, 'Any question');
    await user.click(screen.getByRole('button', { name: /ask gitcontext/i }));

    await waitFor(() => {
      expect(screen.getByTestId('markdown-answer')).toBeInTheDocument();
    });

    const markdownBody = screen.getByTestId('markdown-answer');

    // Valid citation [1] must be a link with target="_blank" and rel="noopener noreferrer"
    const citationLink1 = markdownBody.querySelector('a[href*="VectorStore.java"]');
    expect(citationLink1).not.toBeNull();
    expect(citationLink1?.textContent).toBe('[1]');
    expect(citationLink1).toHaveAttribute('target', '_blank');
    expect(citationLink1).toHaveAttribute('rel', 'noopener noreferrer');

    // Valid citation [2] must be a link
    const citationLink2 = markdownBody.querySelector('a[href*="QdrantClient.java"]');
    expect(citationLink2).not.toBeNull();
    expect(citationLink2?.textContent).toBe('[2]');
    expect(citationLink2).toHaveAttribute('target', '_blank');
    expect(citationLink2).toHaveAttribute('rel', 'noopener noreferrer');

    // Invalid citation [99] must remain plain text, NOT an anchor tag
    const invalidCitationLink = markdownBody.querySelector('a[data-citation-number="99"]');
    expect(invalidCitationLink).toBeNull();
    expect(markdownBody.textContent).toContain('[99]');
  });

  it('prevents XSS: raw HTML like <script> or <img onerror> is not rendered or executed as HTML DOM nodes', async () => {
    const user = userEvent.setup();

    const maliciousResponse: AskResponse = {
      ...mockSuccessResponse,
      answer: 'Normal text <script>window.__xss_flag__ = true;</script> and image <img src="invalid-src" onerror="window.__xss_flag__ = true;" />',
    };

    vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() =>
      Promise.resolve(
        new Response(JSON.stringify(maliciousResponse), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      )
    );

    render(<AskPage />);

    const textarea = screen.getByLabelText(/question/i);
    await user.type(textarea, 'Test for XSS');
    await user.click(screen.getByRole('button', { name: /ask gitcontext/i }));

    await waitFor(() => {
      expect(screen.getByTestId('markdown-answer')).toBeInTheDocument();
    });

    // Verify NO <script> or <img> element was inserted into the DOM
    const markdownBody = screen.getByTestId('markdown-answer');
    expect(markdownBody.querySelector('script')).toBeNull();
    expect(markdownBody.querySelector('img')).toBeNull();
    expect((window as unknown as Record<string, unknown>).__xss_flag__).toBeUndefined();
  });

  it('rejects unsafe protocols: javascript:alert(1) and data:text/html,... are not rendered as links, while normal https GitHub URLs are', async () => {
    const user = userEvent.setup();

    const unsafeUrlResponse: AskResponse = {
      ...mockSuccessResponse,
      answer: 'See safe [1], dangerous [2], and data [3]. Also direct [markdown javascript](javascript:alert(1)).',
      sources: [
        {
          number: 1,
          path: 'backend/src/main/Safe.java',
          startLine: 1,
          endLine: 10,
          chunkType: 'CLASS',
          symbolName: 'Safe',
          url: 'https://github.com/tarunipriya/gitcontext/blob/main/backend/src/main/Safe.java',
          cited: true,
          snippet: 'class Safe {}',
        },
        {
          number: 2,
          path: 'backend/src/main/Evil.java',
          startLine: 1,
          endLine: 10,
          chunkType: 'CLASS',
          symbolName: 'Evil',
          url: 'javascript:alert(1)',
          cited: true,
          snippet: 'class Evil {}',
        },
        {
          number: 3,
          path: 'backend/src/main/Data.java',
          startLine: 1,
          endLine: 10,
          chunkType: 'CLASS',
          symbolName: 'Data',
          url: 'data:text/html,<script>alert(1)</script>',
          cited: true,
          snippet: 'class Data {}',
        },
      ],
    };

    vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() =>
      Promise.resolve(
        new Response(JSON.stringify(unsafeUrlResponse), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      )
    );

    render(<AskPage />);

    const textarea = screen.getByLabelText(/question/i);
    await user.type(textarea, 'Unsafe URL protocol test');
    await user.click(screen.getByRole('button', { name: /ask gitcontext/i }));

    await waitFor(() => {
      expect(screen.getByTestId('markdown-answer')).toBeInTheDocument();
    });

    const markdownBody = screen.getByTestId('markdown-answer');

    // 1. Normal https GitHub URL is rendered as a link
    const safeCitation = markdownBody.querySelector('a[href*="Safe.java"]');
    expect(safeCitation).not.toBeNull();
    expect(safeCitation?.getAttribute('href')).toBe(
      'https://github.com/tarunipriya/gitcontext/blob/main/backend/src/main/Safe.java'
    );
    expect(safeCitation?.textContent).toBe('[1]');

    // 2. javascript:alert(1) citation marker remains plain text (no anchor)
    const jsCitation = markdownBody.querySelector('a[href*="javascript"]');
    expect(jsCitation).toBeNull();
    expect(markdownBody.textContent).toContain('[2]');

    // 3. data:text/html citation marker remains plain text (no anchor)
    const dataCitation = markdownBody.querySelector('a[href*="data:"]');
    expect(dataCitation).toBeNull();
    expect(markdownBody.textContent).toContain('[3]');

    // 4. Direct markdown javascript link is neutralized to plain text
    expect(screen.queryByRole('link', { name: /markdown javascript/i })).toBeNull();
    expect(markdownBody.textContent).toContain('markdown javascript');

    // 5. Sources panel: safe URL has GitHub link, while javascript: and data: have no link
    expect(screen.getByRole('link', { name: /view on github/i })).toHaveAttribute(
      'href',
      'https://github.com/tarunipriya/gitcontext/blob/main/backend/src/main/Safe.java'
    );
    expect(document.querySelector('a[href^="javascript:"]')).toBeNull();
    expect(document.querySelector('a[href^="data:"]')).toBeNull();
  });

  it('renders 404 ProblemDetail error when repository is not indexed', async () => {
    const user = userEvent.setup();

    vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            title: 'Resource Not Found',
            status: 404,
            detail: "Repository 'unknown/repo' not found in keyword or vector index",
          }),
          {
            status: 404,
            headers: { 'Content-Type': 'application/problem+json' },
          }
        )
      )
    );

    render(<AskPage />);

    const textarea = screen.getByLabelText(/question/i);
    await user.type(textarea, 'Is this repo indexed?');
    await user.click(screen.getByRole('button', { name: /ask gitcontext/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    expect(screen.getByText('Resource Not Found')).toBeInTheDocument();
    expect(screen.getByText("Repository 'unknown/repo' not found in keyword or vector index")).toBeInTheDocument();
    expect(screen.getByText('HTTP 404')).toBeInTheDocument();
  });

  it('renders 503 ProblemDetail error when LLM or Qdrant service is unavailable', async () => {
    const user = userEvent.setup();

    vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            title: 'Service Unavailable',
            status: 503,
            detail: 'Qdrant vector database is unavailable at http://localhost:6333',
          }),
          {
            status: 503,
            headers: { 'Content-Type': 'application/problem+json' },
          }
        )
      )
    );

    render(<AskPage />);

    const textarea = screen.getByLabelText(/question/i);
    await user.type(textarea, 'Why is Qdrant down?');
    await user.click(screen.getByRole('button', { name: /ask gitcontext/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    expect(screen.getByText('Service Unavailable')).toBeInTheDocument();
    expect(screen.getByText('Qdrant vector database is unavailable at http://localhost:6333')).toBeInTheDocument();
    expect(screen.getByText('HTTP 503')).toBeInTheDocument();
  });

  it('renders 429 ProblemDetail error when LLM rate limit is exceeded', async () => {
    const user = userEvent.setup();

    vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            title: 'Rate Limit Exceeded',
            status: 429,
            detail: 'Gemini free-tier quota exhausted. Retry after 60s',
          }),
          {
            status: 429,
            headers: { 'Content-Type': 'application/problem+json' },
          }
        )
      )
    );

    render(<AskPage />);

    const textarea = screen.getByLabelText(/question/i);
    await user.type(textarea, 'Rate limit test');
    await user.click(screen.getByRole('button', { name: /ask gitcontext/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    expect(screen.getByText('Rate Limit Exceeded')).toBeInTheDocument();
    expect(screen.getByText('Gemini free-tier quota exhausted. Retry after 60s')).toBeInTheDocument();
    expect(screen.getByText('HTTP 429')).toBeInTheDocument();
  });

  it('validates repository format before sending request', async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    render(<AskPage />);

    const repoInput = screen.getByLabelText(/repository/i);
    const textarea = screen.getByLabelText(/question/i);

    await user.clear(repoInput);
    await user.type(repoInput, 'invalid-no-slash');
    await user.type(textarea, 'A question');
    await user.click(screen.getByRole('button', { name: /ask gitcontext/i }));

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/Repository must be in "owner\/name" format/i)).toBeInTheDocument();
  });
});
