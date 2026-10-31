import React, { useState, useEffect, useRef, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import { Copy, Check, RotateCcw, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Logo } from '../brand/Logo';
import { SourceTypeBadge } from '../brand/SourceTypeBadge';
import { CodeBlock } from '../code/CodeBlock';
import { Composer } from './Composer';
import { SourcePanel } from './SourcePanel';
import { remarkCitations } from '../../utils/remarkCitations';
import { safeHttpUrl } from '../../utils/safeUrl';
import { formatSourceTitle, formatLineRange } from '../../utils/formatters';
import { cn } from '@/lib/utils';
import type { Chat } from '../../types/chat';
import type { RepoSummaryResponse, SourceResponse } from '../../api/client';

export interface ConversationViewProps {
  chat: Chat;
  onSendMessage: (question: string) => void;
  onRetry: (messageId: string) => void;
  onStop: () => void;
  isPending: boolean;
  selectedRepo: string;
  onRepoChange: (repo: string) => void;
  repos: RepoSummaryResponse[];
  reposLoading?: boolean;
}

interface TypewriterMessageProps {
  content: string;
  sources: SourceResponse[];
  revealed?: boolean;
  onOpenSource: (num: number) => void;
  onFinishReveal: () => void;
  onTick?: () => void;
}

const TypewriterMessage: React.FC<TypewriterMessageProps> = ({
  content = '',
  sources,
  revealed,
  onOpenSource,
  onFinishReveal,
  onTick,
}) => {
  const text = content || '';
  const isReducedMotion = typeof window !== 'undefined' &&
    window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const [displayedLength, setDisplayedLength] = useState<number>(() => {
    if (revealed || isReducedMotion) {
      return text.length;
    }
    return Math.min(20, text.length);
  });

  useEffect(() => {
    if (revealed || isReducedMotion) {
      setDisplayedLength(text.length);
      onFinishReveal();
      return;
    }

    if (displayedLength >= text.length) {
      onFinishReveal();
      return;
    }

    const timer = setInterval(() => {
      setDisplayedLength((prev) => {
        const next = Math.min(prev + 4, text.length);
        if (next >= text.length) {
          clearInterval(timer);
          onFinishReveal();
        }
        return next;
      });
      if (onTick) onTick();
    }, 10);

    return () => clearInterval(timer);
  }, [text, revealed, isReducedMotion, onFinishReveal, onTick, displayedLength]);

  const visibleText = text.slice(0, displayedLength);

  return (
    <div className="prose prose-sm dark:prose-invert max-w-none break-words text-sm leading-relaxed text-foreground" data-testid="markdown-answer">
      <ReactMarkdown
        remarkPlugins={sources && sources.length > 0 ? [[remarkCitations, { sources }]] : []}
        components={{
          a: ({
            href,
            children,
            ...props
          }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { 'data-citation-number'?: string | number }) => {
            const citNum = props['data-citation-number'];
            if (citNum !== undefined && citNum !== null) {
              const num = Number(citNum);
              return (
                <button
                  type="button"
                  data-testid={`citation-chip-${num}`}
                  data-citation-number={num}
                  onClick={(e) => {
                    e.preventDefault();
                    onOpenSource(num);
                  }}
                  className="inline-flex items-center justify-center px-1.5 py-0.2 mx-0.5 text-xs font-mono font-medium rounded bg-primary/15 text-primary hover:bg-primary/25 border border-primary/20 transition-colors cursor-pointer select-none align-baseline"
                  title={`View source [${num}]`}
                  aria-label={`Citation [${num}]`}
                >
                  [{num}]
                </button>
              );
            }
            const safe = safeHttpUrl(href);
            if (!safe) return <>{children}</>;
            return (
              <a
                href={safe}
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary underline hover:text-primary/80 transition-colors"
                {...props}
              >
                {children}
              </a>
            );
          },
          code: ({
            inline,
            className,
            children,
            ...props
          }: React.HTMLAttributes<HTMLElement> & { inline?: boolean }) => {
            const match = /language-(\w+)/.exec(className || '');
            const codeString = String(children).replace(/\n$/, '');
            if (!inline && match) {
              return (
                <CodeBlock
                  code={codeString}
                  language={match[1]}
                  className="my-3"
                />
              );
            }
            if (!inline && codeString.includes('\n')) {
              return (
                <CodeBlock
                  code={codeString}
                  language="plaintext"
                  className="my-3"
                />
              );
            }
            return (
              <code className={cn('rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-foreground', className)} {...props}>
                {children}
              </code>
            );
          },
        }}
      >
        {visibleText}
      </ReactMarkdown>
    </div>
  );
};

export const ConversationView: React.FC<ConversationViewProps> = ({
  chat,
  onSendMessage,
  onRetry,
  onStop,
  isPending,
  selectedRepo,
  onRepoChange,
  repos,
  reposLoading,
}) => {
  const [composerQuestion, setComposerQuestion] = useState<string>('');
  const [selectedSource, setSelectedSource] = useState<SourceResponse | null>(null);
  const [isSourcePanelOpen, setIsSourcePanelOpen] = useState<boolean>(false);
  const [copiedMessageId, setCopiedMessageId] = useState<string | null>(null);

  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const bottomAnchorRef = useRef<HTMLDivElement>(null);
  const isUserScrolledUpRef = useRef<boolean>(false);

  const handleScroll = () => {
    const el = scrollContainerRef.current;
    if (!el) return;
    const isScrolledUp = el.scrollHeight - el.scrollTop - el.clientHeight > 80;
    isUserScrolledUpRef.current = isScrolledUp;
  };

  const scrollToBottom = useCallback((smooth = true) => {
    if (!isUserScrolledUpRef.current && bottomAnchorRef.current) {
      bottomAnchorRef.current.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto' });
    }
  }, []);

  useEffect(() => {
    scrollToBottom(false);
  }, [chat.messages.length, scrollToBottom]);

  const handleOpenSourceByNumber = (sourceNumber: number, sources?: SourceResponse[]) => {
    const pool = sources || (chat.messages.find((m) => m.response?.sources)?.response?.sources ?? []);
    const match = pool.find((s) => s.number === sourceNumber);
    if (match) {
      setSelectedSource(match);
      setIsSourcePanelOpen(true);
    }
  };

  const handleCopyAnswer = async (messageId: string, answerText: string) => {
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(answerText);
      } else {
        const ta = document.createElement('textarea');
        ta.value = answerText;
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        ta.remove();
      }
      setCopiedMessageId(messageId);
      setTimeout(() => setCopiedMessageId(null), 2000);
    } catch {
      // Ignore clipboard write failure
    }
  };

  const handleComposerSubmit = (q: string) => {
    setComposerQuestion('');
    isUserScrolledUpRef.current = false;
    onSendMessage(q);
  };

  return (
    <div className="flex flex-col h-full min-h-0 bg-background relative overflow-hidden">
      {/* Scrollable Conversation messages */}
      <main
        ref={scrollContainerRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto px-4 py-6 scroll-smooth"
        tabIndex={0}
        aria-label="Conversation messages"
      >
        <div className="max-w-3xl w-full mx-auto space-y-6">
          {chat.messages.map((message) => {
            if (message.role === 'user') {
              return (
                <div key={message.id} className="flex justify-end" data-testid="user-message">
                  <div className="max-w-[85%] rounded-2xl bg-muted px-4 py-2.5 text-sm text-foreground shadow-2xs whitespace-pre-wrap leading-relaxed">
                    {message.content}
                  </div>
                </div>
              );
            }

            // Assistant Message
            const response = message.response;
            const sources = response?.sources || [];

            return (
              <div key={message.id} className="flex gap-3 items-start" data-testid="assistant-message">
                <div className="shrink-0 mt-1 rounded-lg bg-primary/10 p-1.5 text-primary">
                  <Logo size="sm" showWordmark={false} />
                </div>

                <div className="flex-1 min-w-0 space-y-3">
                  {/* Pending state */}
                  {message.pending && (
                    <div className="flex items-center gap-2.5 text-sm text-muted-foreground py-2" role="status" aria-live="polite">
                      <span className="relative flex h-2.5 w-2.5">
                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75"></span>
                        <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-primary"></span>
                      </span>
                      <span className="font-medium animate-pulse text-xs sm:text-sm">
                        {message.statusText || 'Searching the repository…'}
                      </span>
                    </div>
                  )}

                  {/* Inline Error */}
                  {message.error && (
                    <div className="rounded-xl border border-destructive/40 bg-destructive/10 p-4 space-y-2" role="alert">
                      <div className="flex items-center gap-2 text-destructive font-medium text-sm">
                        <AlertCircle className="h-4 w-4 shrink-0" />
                        <span>{message.error.title || 'Request Failed'}</span>
                        {message.error.status && (
                          <span className="font-mono text-xs px-1.5 py-0.5 rounded bg-destructive/20">
                            HTTP {message.error.status}
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-muted-foreground leading-relaxed">
                        {message.error.detail || 'An unexpected error occurred while communicating with the server.'}
                      </p>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => onRetry(message.id)}
                        className="h-7 text-xs gap-1.5 mt-2 border-destructive/30 hover:bg-destructive/10"
                      >
                        <RotateCcw className="h-3.5 w-3.5" />
                        <span>Retry</span>
                      </Button>
                    </div>
                  )}

                  {/* Assistant Answer Content */}
                  {response && response.answer && (
                    <>
                      <TypewriterMessage
                        content={response.answer}
                        sources={sources}
                        revealed={message.revealed}
                        onOpenSource={(num) => handleOpenSourceByNumber(num, sources)}
                        onFinishReveal={() => {
                          message.revealed = true;
                        }}
                        onTick={() => scrollToBottom(false)}
                      />

                      {/* Source Cards Row */}
                      {sources.length > 0 && (
                        <div className="pt-2">
                          <h4 className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-2">
                            Sources ({sources.length})
                          </h4>
                          <div className="flex flex-wrap gap-2" role="list" aria-label="Referenced sources">
                            {sources.map((src) => {
                              const displayTitle = formatSourceTitle(src);
                              const lineStr = formatLineRange(src.startLine, src.endLine);
                              const type = (src.chunkType || (src.path ? 'CODE' : 'DOC')).toUpperCase();

                              return (
                                <button
                                  key={src.number}
                                  type="button"
                                  role="listitem"
                                  onClick={() => handleOpenSourceByNumber(src.number, sources)}
                                  className={cn(
                                    'flex items-center gap-2 rounded-lg border border-border/80 bg-card hover:bg-muted/80 px-2.5 py-1.5 text-xs text-left transition-all hover:border-primary/50 group cursor-pointer shadow-2xs max-w-full',
                                    src.cited && 'border-primary/40 bg-primary/5'
                                  )}
                                  data-testid={`source-card-${src.number}`}
                                  title={`View source [${src.number}]: ${displayTitle}`}
                                >
                                  <span className="font-mono text-[11px] font-semibold text-muted-foreground group-hover:text-primary">
                                    [{src.number}]
                                  </span>
                                  <SourceTypeBadge type={type} showIcon={true} />
                                  <span className="font-mono text-xs text-foreground truncate max-w-[160px] sm:max-w-[240px]">
                                    {displayTitle}
                                  </span>
                                  {lineStr && (
                                    <span className="font-mono text-[11px] text-muted-foreground shrink-0">
                                      {lineStr}
                                    </span>
                                  )}
                                </button>
                              );
                            })}
                          </div>
                        </div>
                      )}

                      {/* Actions and Telemetry Row */}
                      <div className="flex items-center justify-between pt-2 border-t border-border/30 gap-2 flex-wrap text-xs text-muted-foreground">
                        <div className="flex items-center gap-1.5">
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => handleCopyAnswer(message.id, response.answer)}
                            className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground gap-1"
                            aria-label="Copy answer"
                          >
                            {copiedMessageId === message.id ? (
                              <>
                                <Check className="h-3.5 w-3.5 text-success" />
                                <span>Copied</span>
                              </>
                            ) : (
                              <>
                                <Copy className="h-3.5 w-3.5" />
                                <span>Copy answer</span>
                              </>
                            )}
                          </Button>

                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => onRetry(message.id)}
                            className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground gap-1"
                            aria-label="Retry answer"
                          >
                            <RotateCcw className="h-3.5 w-3.5" />
                            <span>Retry</span>
                          </Button>
                        </div>

                        {/* Muted Telemetry Line */}
                        <div className="font-mono text-[11px] text-muted-foreground/75 truncate select-none">
                          {response.model} · {response.retrievalMillis} ms · {response.generationMillis} ms
                        </div>
                      </div>
                    </>
                  )}
                </div>
              </div>
            );
          })}
          <div ref={bottomAnchorRef} aria-hidden="true" />
        </div>
      </main>

      {/* Pinned Composer at bottom */}
      <div className="border-t border-border/40 bg-background/80 backdrop-blur-md p-4 shrink-0">
        <div className="max-w-3xl w-full mx-auto">
          <Composer
            question={composerQuestion}
            onQuestionChange={setComposerQuestion}
            onSubmit={handleComposerSubmit}
            selectedRepo={selectedRepo}
            onRepoChange={onRepoChange}
            repos={repos}
            reposLoading={reposLoading}
            isPending={isPending}
            onStop={onStop}
            placeholder="Ask a follow-up question..."
          />
        </div>
      </div>

      {/* Right-side Source Panel Sheet */}
      <SourcePanel
        source={selectedSource}
        isOpen={isSourcePanelOpen}
        onClose={() => setIsSourcePanelOpen(false)}
      />
    </div>
  );
};
