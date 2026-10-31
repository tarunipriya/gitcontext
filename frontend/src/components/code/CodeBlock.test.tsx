import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CodeBlock } from './CodeBlock';

describe('CodeBlock Component', () => {
  const sampleCode = `public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello");\n    }\n}`;
  const testPath = 'backend/src/main/java/io/github/tarunipriya/gitcontext/Main.java';

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders file path in monospace header with tooltip', () => {
    render(<CodeBlock code={sampleCode} filePath={testPath} language="java" />);
    const pathEl = screen.getByTestId('codeblock-filepath');
    expect(pathEl).toHaveTextContent(testPath);
    expect(pathEl).toHaveClass('font-mono');
  });

  it('renders line numbers starting at startLine', async () => {
    render(
      <CodeBlock
        code={sampleCode}
        filePath={testPath}
        language="java"
        startLine={42}
        showLineNumbers={true}
      />
    );

    // Initial render or fallback shows line 42, 43, 44, 45, 46
    await waitFor(() => {
      expect(screen.getByText('42')).toBeInTheDocument();
      expect(screen.getByText('43')).toBeInTheDocument();
      expect(screen.getByText('44')).toBeInTheDocument();
    });
  });

  it('copy button calls navigator.clipboard.writeText with the code', async () => {
    const user = userEvent.setup();
    const writeTextMock = vi.fn().mockResolvedValue(undefined);

    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: writeTextMock,
      },
      configurable: true,
      writable: true,
    });

    render(<CodeBlock code={sampleCode} filePath={testPath} language="java" />);

    const copyBtn = screen.getByRole('button', { name: /copy code/i });
    await user.click(copyBtn);

    expect(writeTextMock).toHaveBeenCalledTimes(1);
    expect(writeTextMock).toHaveBeenCalledWith(sampleCode);

    // Feedback "Copied" is displayed
    await waitFor(() => {
      expect(screen.getByText('Copied')).toBeInTheDocument();
    });
  });

  it('copy button falls back to execCommand when navigator.clipboard is unavailable', async () => {
    const user = userEvent.setup();

    // Remove navigator.clipboard
    Object.defineProperty(navigator, 'clipboard', {
      value: undefined,
      configurable: true,
      writable: true,
    });

    const execCommandMock = vi.fn().mockReturnValue(true);
    document.execCommand = execCommandMock;

    render(<CodeBlock code={sampleCode} filePath={testPath} language="java" />);

    const copyBtn = screen.getByRole('button', { name: /copy code/i });
    await user.click(copyBtn);

    expect(execCommandMock).toHaveBeenCalledWith('copy');
    await waitFor(() => {
      expect(screen.getByText('Copied')).toBeInTheDocument();
    });
  });
});
