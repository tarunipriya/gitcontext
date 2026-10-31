import React, { useRef, useEffect, useCallback } from 'react';
import { ArrowUp, Square, FolderGit2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import type { RepoSummaryResponse } from '../../api/client';

export interface ComposerProps {
  question: string;
  onQuestionChange: (value: string) => void;
  onSubmit: (question: string) => void;
  selectedRepo: string;
  onRepoChange: (repo: string) => void;
  repos: RepoSummaryResponse[];
  reposLoading?: boolean;
  isPending: boolean;
  onStop: () => void;
  placeholder?: string;
  autoFocus?: boolean;
  className?: string;
}

export const Composer: React.FC<ComposerProps> = ({
  question,
  onQuestionChange,
  onSubmit,
  selectedRepo,
  onRepoChange,
  repos,
  reposLoading = false,
  isPending,
  onStop,
  placeholder = 'Ask a question about this repository...',
  autoFocus = false,
  className,
}) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const adjustHeight = useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    const newHeight = Math.min(el.scrollHeight, 200);
    el.style.height = `${Math.max(newHeight, 52)}px`;
  }, []);

  useEffect(() => {
    adjustHeight();
  }, [question, adjustHeight]);

  useEffect(() => {
    if (autoFocus && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [autoFocus]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!isPending && question.trim().length > 0) {
        onSubmit(question.trim());
      }
    }
  };

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!isPending && question.trim().length > 0) {
      onSubmit(question.trim());
    }
  };

  return (
    <div className={cn('w-full', className)}>
      <form
        onSubmit={handleFormSubmit}
        className="relative rounded-2xl border border-border bg-card shadow-sm focus-within:border-primary/50 focus-within:ring-2 focus-within:ring-ring/20 transition-all p-3"
      >
        <textarea
          ref={textareaRef}
          value={question}
          onChange={(e) => onQuestionChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          aria-label="Ask a question about this repository"
          rows={2}
          className="w-full resize-none border-0 bg-transparent p-1 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-0 leading-relaxed max-h-[200px] overflow-y-auto"
        />

        <div className="flex items-center justify-between pt-2 border-t border-border/40 mt-1 gap-2">
          {/* Repository Picker inside bottom-left */}
          <div className="flex items-center gap-1.5 min-w-0 max-w-[240px] sm:max-w-[320px]">
            <FolderGit2 className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
            <select
              aria-label="Repository"
              data-testid="repo-picker"
              value={selectedRepo}
              onChange={(e) => onRepoChange(e.target.value)}
              disabled={isPending || reposLoading}
              className="h-7 text-xs bg-muted/60 text-foreground hover:bg-muted font-medium rounded-lg px-2 border-0 focus:outline-none focus:ring-1 focus:ring-ring cursor-pointer truncate max-w-full"
            >
              {repos.length === 0 ? (
                <option value={selectedRepo || 'no-repo'}>
                  {reposLoading ? 'Loading repositories...' : selectedRepo || 'No repositories indexed'}
                </option>
              ) : (
                repos.map((r) => (
                  <option key={r.repo} value={r.repo}>
                    {r.repo}
                  </option>
                ))
              )}
            </select>
          </div>

          {/* Action buttons bottom-right */}
          <div className="flex items-center gap-2 shrink-0">
            {isPending ? (
              <Button
                type="button"
                variant="destructive"
                size="sm"
                onClick={onStop}
                aria-label="Stop generating"
                className="h-8 rounded-xl px-3 text-xs gap-1.5 font-medium shadow-xs"
              >
                <Square className="h-3.5 w-3.5 fill-current" />
                <span>Stop</span>
              </Button>
            ) : (
              <Button
                type="submit"
                size="sm"
                disabled={question.trim().length === 0}
                aria-label="Send message"
                className="h-8 w-8 p-0 rounded-xl bg-primary text-primary-foreground hover:bg-primary/90 transition-all disabled:opacity-30 disabled:cursor-not-allowed shadow-xs shrink-0 flex items-center justify-center"
              >
                <ArrowUp className="h-4 w-4" />
              </Button>
            )}
          </div>
        </div>
      </form>

      <p className="text-[11px] text-muted-foreground/75 text-center mt-2 select-none tracking-tight">
        Answers are generated from the indexed repository and may be incomplete.
      </p>
    </div>
  );
};
