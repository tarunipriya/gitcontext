import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Logo } from './Logo';

describe('Logo Component', () => {
  it('renders with an accessible name', () => {
    render(<Logo />);
    const logoSvg = screen.getByRole('img', { name: /gitcontext logo/i });
    expect(logoSvg).toBeInTheDocument();
  });

  it('renders wordmark by default and hides it when showWordmark is false', () => {
    const { rerender } = render(<Logo showWordmark={true} />);
    expect(screen.getByText('Git')).toBeInTheDocument();
    expect(screen.getByText('Context')).toBeInTheDocument();

    rerender(<Logo showWordmark={false} />);
    expect(screen.queryByText('Git')).not.toBeInTheDocument();
    expect(screen.queryByText('Context')).not.toBeInTheDocument();
  });

  it('renders different size classes', () => {
    const { container, rerender } = render(<Logo size="sm" />);
    expect(container.querySelector('svg')).toHaveClass('h-4', 'w-4');

    rerender(<Logo size="md" />);
    expect(container.querySelector('svg')).toHaveClass('h-6', 'w-6');

    rerender(<Logo size="lg" />);
    expect(container.querySelector('svg')).toHaveClass('h-8', 'w-8');
  });
});
