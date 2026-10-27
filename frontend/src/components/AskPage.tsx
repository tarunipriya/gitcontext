import React, { useState, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import { askRepo, ApiError, type AskResponse, type ProblemDetail, type SourceResponse } from '../api/client';
import { remarkCitations } from '../utils/remarkCitations';
import { safeHttpUrl } from '../utils/safeUrl';
import { formatSourceTitle } from '../utils/formatters';
import { AppHeader } from './AppHeader';
import './AskPage.css';

const STORAGE_KEY = 'gitcontext:last_repo';
const DEFAULT_REPO = 'tarunipriya/gitcontext';

function getInitialRepo(): string {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved && saved.trim().length > 0 ? saved.trim() : DEFAULT_REPO;
  } catch {
    return DEFAULT_REPO;
  }
}

function persistRepo(repo: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, repo.trim());
  } catch {
    // Gracefully ignore local storage restrictions/quotas
  }
}

function formatLineRange(start?: number | null, end?: number | null): string | null {
  if (start == null) return null;
  if (end == null || end === start) return `L${start}`;
  return `L${start}-${end}`;
}

export interface AskPageProps {
  initialRepo?: string;
}

export const AskPage: React.FC<AskPageProps> = ({ initialRepo }) => {
  const [repo, setRepo] = useState<string>(() => initialRepo || getInitialRepo());
  const [question, setQuestion] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<ProblemDetail | null>(null);
  const [response, setResponse] = useState<AskResponse | null>(null);

  useEffect(() => {
    if (initialRepo && initialRepo.trim().length > 0) {
      setRepo(initialRepo.trim());
      persistRepo(initialRepo.trim());
    }
  }, [initialRepo]);

  const handleRepoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setRepo(val);
    persistRepo(val);
  };

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) {
      e.preventDefault();
    }
    const trimmedQuestion = question.trim();
    if (!trimmedQuestion || loading) {
      return;
    }

    const trimmedRepo = repo.trim();
    const slashIdx = trimmedRepo.indexOf('/');
    if (slashIdx <= 0 || slashIdx === trimmedRepo.length - 1 || trimmedRepo.indexOf('/', slashIdx + 1) !== -1) {
      setError({
        title: 'Invalid Repository',
        detail: 'Repository must be in "owner/name" format (e.g. tarunipriya/gitcontext)',
        status: 400,
      });
      return;
    }

    const owner = trimmedRepo.substring(0, slashIdx);
    const name = trimmedRepo.substring(slashIdx + 1);

    setLoading(true);
    setError(null);

    try {
      const res = await askRepo(owner, name, {
        question: trimmedQuestion,
        mode: 'HYBRID',
        excludeTests: true,
      });
      setResponse(res);
      persistRepo(trimmedRepo);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(
          err.problemDetail ?? {
            title: err.title ?? 'Error',
            detail: err.detail ?? err.message,
            status: err.status,
          }
        );
      } else if (err instanceof Error) {
        setError({
          title: 'Request Failed',
          detail: err.message,
        });
      } else {
        setError({
          title: 'Unknown Error',
          detail: 'An unexpected error occurred while communicating with the server.',
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      handleSubmit();
    }
  };

  return (
    <div className="ask-container">
      <AppHeader currentTab="ask" />

      <main className="ask-main">
        <form className="ask-form" onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label htmlFor="repo-input" className="form-label">
              Repository
            </label>
            <input
              id="repo-input"
              type="text"
              className="form-input"
              value={repo}
              onChange={handleRepoChange}
              placeholder="owner/name (e.g. tarunipriya/gitcontext)"
              disabled={loading}
              autoComplete="off"
              spellCheck="false"
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="question-input" className="form-label">
              Question
            </label>
            <textarea
              id="question-input"
              className="form-textarea"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Ask anything about the codebase, architecture, PRs or commits... (Ctrl+Enter to submit)"
              rows={4}
              disabled={loading}
              required
            />
          </div>

          <div className="form-actions">
            <span className="shortcut-hint" aria-hidden="true">
              Press <kbd>Ctrl</kbd> + <kbd>Enter</kbd> to submit
            </span>
            <button
              id="ask-button"
              type="submit"
              className="ask-button"
              disabled={loading || question.trim().length === 0}
            >
              {loading ? (
                <>
                  <span className="spinner" aria-hidden="true" />
                  <span>Searching & Generating...</span>
                </>
              ) : (
                <>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <circle cx="11" cy="11" r="8" />
                    <line x1="21" y1="21" x2="16.65" y2="16.65" />
                  </svg>
                  <span>Ask GitContext</span>
                </>
              )}
            </button>
          </div>
        </form>

        {loading && (
          <section className="loading-card" role="status" aria-live="polite">
            <div className="loading-indicator">
              <span className="spinner large" aria-hidden="true" />
              <div className="loading-text">
                <strong>Retrieving repository knowledge...</strong>
                <p>Synthesizing dense vector search, BM25 keywords, and grounded citations.</p>
              </div>
            </div>
          </section>
        )}

        {error && (
          <section className="error-card" role="alert" aria-live="assertive">
            <div className="error-header">
              <svg className="error-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              <h2 className="error-title">{error.title || 'Request Failed'}</h2>
              {error.status && <span className="error-status">HTTP {error.status}</span>}
            </div>
            <p className="error-detail">{error.detail || 'An unexpected error occurred. Please check server logs.'}</p>
          </section>
        )}

        {response && !loading && (
          <article className="response-container">
            <section className="answer-section">
              <div className="section-header">
                <h2 className="section-title">Answer</h2>
                <span className="citation-count-badge">
                  {response.citations.length} verified citation{response.citations.length === 1 ? '' : 's'}
                </span>
              </div>
              <div className="markdown-body" data-testid="markdown-answer">
                <ReactMarkdown
                  remarkPlugins={[[remarkCitations, { sources: response.sources }]]}
                  components={{
                    a: ({ href, children, ...props }) => {
                      const safe = safeHttpUrl(href);
                      if (!safe) {
                        return <>{children}</>;
                      }
                      return (
                        <a href={safe} target="_blank" rel="noopener noreferrer" {...props}>
                          {children}
                        </a>
                      );
                    },
                  }}
                >
                  {response.answer}
                </ReactMarkdown>
              </div>

              {response.warnings && response.warnings.length > 0 && (
                <div className="warnings-banner" role="note">
                  <strong>Notice:</strong>
                  <ul>
                    {response.warnings.map((warn, i) => (
                      <li key={i}>{warn}</li>
                    ))}
                  </ul>
                </div>
              )}
            </section>

            {response.sources && response.sources.length > 0 && (
              <section className="sources-section" aria-labelledby="sources-heading">
                <div className="section-header">
                  <h2 id="sources-heading" className="section-title">
                    Retrieved Sources ({response.sources.length})
                  </h2>
                </div>
                <ol className="sources-list">
                  {response.sources.map((source: SourceResponse) => {
                    const displayTitle = formatSourceTitle(source);
                    const lineStr = formatLineRange(source.startLine, source.endLine);
                    const safeSourceUrl = safeHttpUrl(source.url);
                    return (
                      <li key={source.number} className={`source-item ${source.cited ? 'is-cited' : ''}`}>
                        <div className="source-item-header">
                          <span className="source-number">[{source.number}]</span>
                          <span className="source-path" title={displayTitle}>
                            {displayTitle}
                          </span>
                          {lineStr && <span className="source-lines">{lineStr}</span>}
                          {source.symbolName && (
                            <span className="source-symbol" title={source.symbolName}>
                              <code>{source.symbolName}</code>
                            </span>
                          )}
                          {source.chunkType && (
                            <span className="source-chunk-type">{source.chunkType}</span>
                          )}
                          {source.cited && <span className="source-badge-cited">cited</span>}
                        </div>

                        {source.snippet && (
                          <pre className="source-snippet">
                            <code>{source.snippet}</code>
                          </pre>
                        )}

                        {safeSourceUrl && (
                          <div className="source-footer">
                            <a
                              href={safeSourceUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="source-github-link"
                            >
                              <span>View on GitHub</span>
                              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                                <polyline points="15 3 21 3 21 9" />
                                <line x1="10" y1="14" x2="21" y2="3" />
                              </svg>
                            </a>
                          </div>
                        )}
                      </li>
                    );
                  })}
                </ol>
              </section>
            )}

            <footer className="response-footer">
              <div className="telemetry-item">
                <span className="telemetry-label">Model:</span>
                <span className="telemetry-value">{response.model}</span>
              </div>
              <div className="telemetry-separator" aria-hidden="true">•</div>
              <div className="telemetry-item">
                <span className="telemetry-label">Retrieval:</span>
                <span className="telemetry-value">{response.retrievalMillis} ms</span>
              </div>
              <div className="telemetry-separator" aria-hidden="true">•</div>
              <div className="telemetry-item">
                <span className="telemetry-label">Generation:</span>
                <span className="telemetry-value">{response.generationMillis} ms</span>
              </div>
              <div className="telemetry-separator" aria-hidden="true">•</div>
              <div className="telemetry-item">
                <span className="telemetry-label">Total:</span>
                <span className="telemetry-value">{response.totalMillis} ms</span>
              </div>
              {(response.promptTokens > 0 || response.outputTokens > 0) && (
                <>
                  <div className="telemetry-separator" aria-hidden="true">•</div>
                  <div className="telemetry-item">
                    <span className="telemetry-label">Tokens:</span>
                    <span className="telemetry-value">
                      {response.promptTokens} in / {response.outputTokens} out
                    </span>
                  </div>
                </>
              )}
            </footer>
          </article>
        )}
      </main>
    </div>
  );
};
