import React, { useState, Suspense, lazy } from 'react';
import { Copy, Check, FileCode } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

const LazyHighlighter = lazy(() => import('./SyntaxHighlighter'));

export interface CodeBlockProps extends React.HTMLAttributes<HTMLDivElement> {
  code: string;
  language?: string;
  filePath?: string;
  startLine?: number;
  showLineNumbers?: boolean;
  maxHeight?: string;
}

const PlainCodeFallback: React.FC<{
  code: string;
  startLine: number;
  showLineNumbers: boolean;
}> = ({ code, startLine, showLineNumbers }) => {
  const lines = code.trimEnd().split('\n');
  return (
    <pre className="font-mono text-xs leading-relaxed m-0 bg-transparent text-foreground">
      {lines.map((line, i) => (
        <div key={i} className="table-row hover:bg-muted/40 transition-colors">
          {showLineNumbers && (
            <span
              className="table-cell select-none pr-4 pl-3 py-0.5 text-right font-mono text-xs text-muted-foreground/50 border-r border-border/40"
              aria-hidden="true"
              data-testid="line-number"
            >
              {startLine + i}
            </span>
          )}
          <span className="table-cell pl-4 pr-4 py-0.5 whitespace-pre">{line || ' '}</span>
        </div>
      ))}
    </pre>
  );
};

export const CodeBlock: React.FC<CodeBlockProps> = ({
  code,
  language = 'plaintext',
  filePath,
  startLine = 1,
  showLineNumbers = true,
  maxHeight = 'max-h-[450px]',
  className,
  ...props
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(code);
      } else if (typeof document !== 'undefined') {
        const textArea = document.createElement('textarea');
        textArea.value = code;
        textArea.style.position = 'fixed';
        textArea.style.left = '-999999px';
        textArea.style.top = '-999999px';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        document.execCommand('copy');
        textArea.remove();
      }
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy to clipboard', err);
    }
  };

  return (
    <div
      className={cn(
        'group relative rounded-xl border border-border bg-card text-card-foreground shadow-xs overflow-hidden',
        className
      )}
      {...props}
    >
      {/* Header with file path and copy button */}
      <div className="flex items-center justify-between border-b border-border bg-muted/40 px-3.5 py-2 text-xs">
        <div className="flex items-center gap-2 min-w-0 mr-2">
          <FileCode className="h-4 w-4 text-muted-foreground shrink-0" />
          {filePath ? (
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <span
                    className="font-mono text-xs text-muted-foreground hover:text-foreground truncate block max-w-[240px] sm:max-w-[420px] md:max-w-[560px] cursor-default transition-colors"
                    data-testid="codeblock-filepath"
                  >
                    {filePath}
                  </span>
                </TooltipTrigger>
                <TooltipContent side="top">
                  <p className="font-mono text-xs">{filePath}</p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          ) : (
            <span className="font-mono text-xs text-muted-foreground uppercase">{language}</span>
          )}
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {filePath && (
            <span className="hidden sm:inline-block font-mono text-[11px] text-muted-foreground/70 uppercase px-1.5 py-0.5 rounded-sm bg-muted/60">
              {language}
            </span>
          )}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleCopy}
            aria-label="Copy code"
            title="Copy code"
            className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground hover:bg-muted"
          >
            {copied ? (
              <>
                <Check className="h-3.5 w-3.5 text-success mr-1" />
                <span className="text-[11px]">Copied</span>
              </>
            ) : (
              <>
                <Copy className="h-3.5 w-3.5 mr-1" />
                <span className="text-[11px]">Copy</span>
              </>
            )}
          </Button>
        </div>
      </div>

      {/* Code body with horizontal scroll inside block only and vertical scroll bounded by maxHeight */}
      <div className={cn('overflow-y-auto overflow-x-auto p-3', maxHeight)}>
        <Suspense
          fallback={
            <PlainCodeFallback
              code={code}
              startLine={startLine}
              showLineNumbers={showLineNumbers}
            />
          }
        >
          <LazyHighlighter
            code={code}
            language={language}
            startLine={startLine}
            showLineNumbers={showLineNumbers}
          />
        </Suspense>
      </div>
    </div>
  );
};
