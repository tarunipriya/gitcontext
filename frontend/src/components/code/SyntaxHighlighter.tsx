import React, { useContext } from 'react';
import { Highlight, themes, Prism } from 'prism-react-renderer';
import { ThemeContext } from '@/components/theme';

// Ensure Prism global is available before loading additional grammars
if (typeof window !== 'undefined') {
  (window as unknown as { Prism: typeof Prism }).Prism = Prism;
} else if (typeof global !== 'undefined') {
  (global as unknown as { Prism: typeof Prism }).Prism = Prism;
}

// Load Java and Bash grammars into Prism
try {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  require('prismjs/components/prism-java');
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  require('prismjs/components/prism-bash');
} catch {
  // Grammars may already be loaded or in SSR
}

export interface SyntaxHighlighterProps {
  code: string;
  language: string;
  startLine: number;
  showLineNumbers: boolean;
}

function normalizeLanguage(lang: string): string {
  const l = lang.toLowerCase().trim();
  switch (l) {
    case 'java':
      return 'java';
    case 'python':
    case 'py':
      return 'python';
    case 'typescript':
    case 'ts':
      return 'typescript';
    case 'tsx':
      return 'tsx';
    case 'javascript':
    case 'js':
      return 'javascript';
    case 'jsx':
      return 'jsx';
    case 'json':
      return 'json';
    case 'yaml':
    case 'yml':
      return 'yaml';
    case 'xml':
    case 'html':
    case 'svg':
      return 'markup';
    case 'sql':
      return 'sql';
    case 'markdown':
    case 'md':
      return 'markdown';
    case 'bash':
    case 'sh':
    case 'shell':
    case 'zsh':
      return 'bash';
    default:
      return Prism.languages[l] ? l : 'plaintext';
  }
}

export const SyntaxHighlighter: React.FC<SyntaxHighlighterProps> = ({
  code,
  language,
  startLine,
  showLineNumbers,
}) => {
  const themeContext = useContext(ThemeContext);
  const isLight = themeContext?.resolvedTheme === 'light';

  const normalizedLang = normalizeLanguage(language);
  const theme = isLight ? themes.github : themes.vsDark;

  return (
    <Highlight
      theme={theme}
      code={code.trimEnd()}
      language={normalizedLang}
    >
      {({ className, style, tokens, getLineProps, getTokenProps }) => (
        <pre
          className={`font-mono text-xs leading-relaxed ${className}`}
          style={{ ...style, backgroundColor: 'transparent', margin: 0 }}
        >
          {tokens.map((line, i) => {
            const lineNumber = startLine + i;
            return (
              <div
                key={i}
                {...getLineProps({ line })}
                className="table-row hover:bg-muted/40 transition-colors"
              >
                {showLineNumbers && (
                  <span
                    className="table-cell select-none pr-4 pl-3 py-0.5 text-right font-mono text-xs text-muted-foreground/50 border-r border-border/40"
                    aria-hidden="true"
                    data-testid="line-number"
                  >
                    {lineNumber}
                  </span>
                )}
                <span className="table-cell pl-4 pr-4 py-0.5 whitespace-pre">
                  {line.map((token, key) => (
                    <span key={key} {...getTokenProps({ token })} />
                  ))}
                </span>
              </div>
            );
          })}
        </pre>
      )}
    </Highlight>
  );
};

export default SyntaxHighlighter;
