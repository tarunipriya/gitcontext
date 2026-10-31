import React from 'react';
import { ExternalLink, Tag } from 'lucide-react';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { SourceTypeBadge } from '../brand/SourceTypeBadge';
import { CodeBlock } from '../code/CodeBlock';
import { formatSourceTitle, formatLineRange } from '../../utils/formatters';
import { safeHttpUrl } from '../../utils/safeUrl';
import type { SourceResponse } from '../../api/client';

export interface SourcePanelProps {
  source: SourceResponse | null;
  isOpen: boolean;
  onClose: () => void;
}

function getLanguageFromPath(path?: string | null): string {
  if (!path) return 'plaintext';
  const ext = path.split('.').pop()?.toLowerCase();
  switch (ext) {
    case 'java':
      return 'java';
    case 'ts':
    case 'tsx':
      return 'typescript';
    case 'js':
    case 'jsx':
      return 'javascript';
    case 'py':
      return 'python';
    case 'go':
      return 'go';
    case 'rs':
      return 'rust';
    case 'md':
      return 'markdown';
    case 'json':
      return 'json';
    case 'yml':
    case 'yaml':
      return 'yaml';
    case 'xml':
      return 'xml';
    case 'html':
      return 'html';
    case 'css':
      return 'css';
    default:
      return 'plaintext';
  }
}

export const SourcePanel: React.FC<SourcePanelProps> = ({
  source,
  isOpen,
  onClose,
}) => {
  if (!source) return null;

  const displayTitle = formatSourceTitle(source);
  const lineRangeStr = formatLineRange(source.startLine, source.endLine);
  const safeUrl = safeHttpUrl(source.url);
  const sourceType = (source.chunkType || (source.path ? 'CODE' : 'DOC')).toUpperCase();
  const language = getLanguageFromPath(source.path);

  return (
    <Sheet open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-xl md:max-w-2xl p-0 flex flex-col bg-card border-border overflow-hidden"
        data-testid="source-panel"
        aria-label="Source details panel"
      >
        <SheetHeader className="p-4 border-b border-border bg-muted/30 shrink-0">
          <div className="flex items-center gap-2 mb-2 flex-wrap">
            <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded bg-primary/15 text-primary border border-primary/20">
              [{source.number}]
            </span>
            <SourceTypeBadge type={sourceType} />
            {source.chunkType && (
              <span className="text-[11px] font-mono text-muted-foreground uppercase px-1.5 py-0.5 rounded bg-muted">
                {source.chunkType}
              </span>
            )}
            {source.cited && (
              <span className="text-[11px] font-medium text-primary px-1.5 py-0.5 rounded bg-primary/10">
                Cited in answer
              </span>
            )}
          </div>

          <SheetTitle className="text-sm font-mono text-foreground font-semibold break-all text-left" data-testid="source-panel-path">
            {displayTitle}
          </SheetTitle>

          <div className="flex items-center gap-3 text-xs text-muted-foreground pt-1 flex-wrap">
            {lineRangeStr && (
              <span className="font-mono font-medium text-foreground" data-testid="source-panel-lines">
                Lines: {lineRangeStr}
              </span>
            )}
            {source.symbolName && (
              <span className="inline-flex items-center gap-1 font-mono text-xs text-foreground bg-muted/60 px-1.5 py-0.5 rounded">
                <Tag className="h-3 w-3 text-muted-foreground" />
                {source.symbolName}
              </span>
            )}
          </div>
        </SheetHeader>

        {/* Source Code Snippet */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          <div>
            <h4 className="text-xs font-medium text-muted-foreground mb-2 select-none uppercase tracking-wider">
              Source Snippet
            </h4>
            <CodeBlock
              code={source.snippet || '// No snippet text provided'}
              language={language}
              filePath={source.path || undefined}
              startLine={source.startLine || 1}
              showLineNumbers={true}
              maxHeight="max-h-[65vh]"
            />
          </div>
        </div>

        {/* Footer with GitHub Link */}
        {safeUrl && (
          <div className="p-4 border-t border-border bg-muted/20 shrink-0 flex items-center justify-between">
            <a
              href={safeUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:text-primary/80 transition-colors"
              data-testid="source-panel-github-link"
            >
              <span>View on GitHub</span>
              <ExternalLink className="h-3.5 w-3.5" />
            </a>

            <span className="text-[11px] text-muted-foreground">External link</span>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
};
