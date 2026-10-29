import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, useTheme } from './ThemeProvider';
import { ThemeToggle } from './ThemeToggle';

const ConsumerComponent = () => {
  const { theme, resolvedTheme, setTheme } = useTheme();
  return (
    <div>
      <span data-testid="theme">{theme}</span>
      <span data-testid="resolved">{resolvedTheme}</span>
      <button onClick={() => setTheme('light')}>Set Light</button>
      <button onClick={() => setTheme('dark')}>Set Dark</button>
      <button onClick={() => setTheme('system')}>Set System</button>
    </div>
  );
};

describe('ThemeProvider & ThemeToggle', () => {
  let mediaListeners: Array<(e: { matches: boolean }) => void> = [];
  let matchesDark = true;

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
    mediaListeners = [];
    matchesDark = true;

    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: matchesDark,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn((_event: string, cb: (e: { matches: boolean }) => void) => {
        mediaListeners.push(cb);
      }),
      removeEventListener: vi.fn((_event: string, cb: (e: { matches: boolean }) => void) => {
        mediaListeners = mediaListeners.filter((l) => l !== cb);
      }),
      dispatchEvent: vi.fn(),
    }));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('defaults to dark theme when no stored preference exists', () => {
    render(
      <ThemeProvider>
        <ConsumerComponent />
      </ThemeProvider>
    );

    expect(screen.getByTestId('theme')).toHaveTextContent('dark');
    expect(screen.getByTestId('resolved')).toHaveTextContent('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.classList.contains('light')).toBe(false);
  });

  it('persists theme selection to localStorage and updates html class', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <ConsumerComponent />
      </ThemeProvider>
    );

    await user.click(screen.getByRole('button', { name: /set light/i }));

    expect(screen.getByTestId('theme')).toHaveTextContent('light');
    expect(screen.getByTestId('resolved')).toHaveTextContent('light');
    expect(localStorage.getItem('gitcontext-theme')).toBe('light');
    expect(document.documentElement.classList.contains('light')).toBe(true);
    expect(document.documentElement.classList.contains('dark')).toBe(false);

    await user.click(screen.getByRole('button', { name: /set dark/i }));
    expect(screen.getByTestId('theme')).toHaveTextContent('dark');
    expect(localStorage.getItem('gitcontext-theme')).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('follows system media query when system theme is active', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <ConsumerComponent />
      </ThemeProvider>
    );

    await user.click(screen.getByRole('button', { name: /set system/i }));
    expect(screen.getByTestId('theme')).toHaveTextContent('system');
    expect(screen.getByTestId('resolved')).toHaveTextContent('dark');

    // Simulate system preference change to light
    act(() => {
      matchesDark = false;
      for (const listener of mediaListeners) {
        listener({ matches: false });
      }
    });

    expect(screen.getByTestId('resolved')).toHaveTextContent('light');
    expect(document.documentElement.classList.contains('light')).toBe(true);

    // Simulate system preference change back to dark
    act(() => {
      matchesDark = true;
      for (const listener of mediaListeners) {
        listener({ matches: true });
      }
    });

    expect(screen.getByTestId('resolved')).toHaveTextContent('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('ThemeToggle allows switching between Light, Dark, and System', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <ThemeToggle />
        <ConsumerComponent />
      </ThemeProvider>
    );

    const lightBtn = screen.getByRole('button', { name: /light theme/i });
    const darkBtn = screen.getByRole('button', { name: /dark theme/i });
    const systemBtn = screen.getByRole('button', { name: /system theme/i });

    expect(darkBtn).toHaveAttribute('aria-pressed', 'true');

    await user.click(lightBtn);
    expect(lightBtn).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('theme')).toHaveTextContent('light');

    await user.click(systemBtn);
    expect(systemBtn).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('theme')).toHaveTextContent('system');
  });

  it('throws an error when useTheme is called outside ThemeProvider', () => {
    // Suppress console.error for expected thrown error
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<ConsumerComponent />)).toThrow(
      'useTheme must be used within a ThemeProvider'
    );
    spy.mockRestore();
  });
});
