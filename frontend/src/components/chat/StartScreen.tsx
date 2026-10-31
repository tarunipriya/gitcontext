import React, { useState } from 'react';
import { Sparkles, ArrowRight, FolderPlus } from 'lucide-react';
import { Logo } from '../brand/Logo';
import { Composer } from './Composer';
import type { RepoSummaryResponse } from '../../api/client';

export interface StartScreenProps {
  selectedRepo: string;
  onRepoChange: (repo: string) => void;
  repos: RepoSummaryResponse[];
  reposLoading?: boolean;
  onStartChat: (question: string) => void;
  isPending?: boolean;
  onStop?: () => void;
}

const EXAMPLE_QUESTIONS = [
  'How is the project structured and what are the main packages?',
  'Where is the core indexing and chunking logic located?',
  'How does hybrid search combine vector and BM25 retrieval?',
  'What external services and environment variables are required?',
];

export const StartScreen: React.FC<StartScreenProps> = ({
  selectedRepo,
  onRepoChange,
  repos,
  reposLoading = false,
  onStartChat,
  isPending = false,
  onStop = () => {},
}) => {
  const [question, setQuestion] = useState<string>('');

  const hasNoRepos = !reposLoading && repos.length === 0;

  const handleExampleClick = (example: string) => {
    onStartChat(example);
  };

  const handleComposerSubmit = (q: string) => {
    onStartChat(q);
  };

  return (
    <div className="flex-1 overflow-y-auto flex flex-col items-center justify-center min-h-full px-4 py-8 bg-background">
      <div className="max-w-3xl w-full mx-auto flex flex-col items-center text-center my-auto">
        {/* Brand Logo mark */}
        <div className="mb-4 p-3 rounded-2xl bg-primary/10 border border-primary/20 text-primary shadow-sm">
          <Logo size="lg" showWordmark={false} />
        </div>

        {/* Heading */}
        <h1 className="text-2xl sm:text-3xl font-semibold tracking-tight text-foreground mb-8">
          What do you want to know about this repo?
        </h1>

        {/* Empty state if no repos are indexed */}
        {hasNoRepos ? (
          <div
            className="w-full rounded-2xl border border-dashed border-border bg-card/60 p-8 text-center space-y-4 my-4"
            data-testid="no-repos-empty-state"
          >
            <div className="inline-flex p-3 rounded-xl bg-muted text-muted-foreground">
              <FolderPlus className="h-6 w-6" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">No repositories indexed yet</h2>
              <p className="text-xs text-muted-foreground mt-1 max-w-md mx-auto">
                Ingest a GitHub repository first so GitContext can parse code, commits, and PRs into the hybrid search index.
              </p>
            </div>
            <a
              href="#/repos"
              className="inline-flex items-center gap-2 rounded-xl bg-primary text-primary-foreground hover:bg-primary/90 px-4 py-2 text-xs font-medium transition-colors shadow-xs"
            >
              <span>Go to Repositories</span>
              <ArrowRight className="h-3.5 w-3.5" />
            </a>
          </div>
        ) : (
          /* Composer */
          <div className="w-full">
            <Composer
              question={question}
              onQuestionChange={setQuestion}
              onSubmit={handleComposerSubmit}
              selectedRepo={selectedRepo}
              onRepoChange={onRepoChange}
              repos={repos}
              reposLoading={reposLoading}
              isPending={isPending}
              onStop={onStop}
              placeholder="Ask anything about the codebase, architecture, PRs or commits..."
              autoFocus={true}
            />

            {/* 4 Example question chips */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 w-full mt-6 text-left" role="list" aria-label="Example questions">
              {EXAMPLE_QUESTIONS.map((exampleText, idx) => (
                <button
                  key={idx}
                  type="button"
                  role="listitem"
                  onClick={() => handleExampleClick(exampleText)}
                  className="flex items-center justify-between gap-3 p-3.5 rounded-xl border border-border/70 bg-card hover:bg-muted/80 hover:border-primary/40 transition-all text-xs sm:text-sm text-muted-foreground hover:text-foreground text-left shadow-2xs group cursor-pointer"
                  data-testid={`example-chip-${idx}`}
                >
                  <div className="flex items-start gap-2.5 min-w-0">
                    <Sparkles className="h-4 w-4 text-primary shrink-0 mt-0.5 opacity-80 group-hover:opacity-100" />
                    <span className="line-clamp-2 leading-snug">{exampleText}</span>
                  </div>
                  <ArrowRight className="h-3.5 w-3.5 text-muted-foreground group-hover:text-primary shrink-0 transition-transform group-hover:translate-x-0.5" />
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
