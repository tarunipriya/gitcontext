import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StyleGuidePage } from './StyleGuidePage';
import { ThemeProvider } from './theme';

describe('StyleGuidePage Component', () => {
  it('renders the style guide with all sections, logo, tokens, and components', () => {
    // Mock matchMedia for ThemeToggle inside StyleGuidePage
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: true,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    render(
      <ThemeProvider>
        <StyleGuidePage />
      </ThemeProvider>
    );

    // Title and intro
    expect(
      screen.getByRole('heading', { name: /gitcontext design system & component library/i })
    ).toBeInTheDocument();

    // Section 1: Logo
    expect(screen.getByRole('heading', { name: /1\. brand logo/i })).toBeInTheDocument();

    // Section 2: Tokens
    expect(
      screen.getByRole('heading', { name: /2\. design tokens \(colors & swatches\)/i })
    ).toBeInTheDocument();

    // Section 3: Typography
    expect(
      screen.getByRole('heading', { name: /3\. typography scale/i })
    ).toBeInTheDocument();

    // Section 4: Stat Cards & Source Type Badges
    expect(
      screen.getByRole('heading', { name: /4\. stat cards & source type badges/i })
    ).toBeInTheDocument();
    expect(screen.getByText('Indexed Points')).toBeInTheDocument();
    expect(screen.getByText('1,248')).toBeInTheDocument();
    expect(screen.getByText('Search Latency')).toBeInTheDocument();
    expect(screen.getByText('42 ms')).toBeInTheDocument();

    // Section 5: CodeBlock
    expect(screen.getByRole('heading', { name: /5\. codeblock component/i })).toBeInTheDocument();
    expect(screen.getByText(/HybridSearchService\.java/)).toBeInTheDocument();

    // Section 6: UI Components
    expect(
      screen.getByRole('heading', { name: /6\. shadcn\/ui components & states/i })
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /primary button/i })).toBeInTheDocument();
  });

  it('renders inside a centered container element with max-width and horizontal padding classes', () => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: true,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    render(
      <ThemeProvider>
        <StyleGuidePage />
      </ThemeProvider>
    );

    const container = screen.getByTestId('styleguide-container');
    expect(container).toBeInTheDocument();
    expect(container).toHaveClass('max-w-6xl');
    expect(container).toHaveClass('mx-auto');
    expect(container).toHaveClass('px-4');
    expect(container).toHaveClass('sm:px-6');
    expect(container).toHaveClass('lg:px-8');
  });
});
