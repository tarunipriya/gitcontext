import * as React from 'react';
import { cn } from '@/lib/utils';

export interface LogoProps extends React.HTMLAttributes<HTMLDivElement> {
  size?: 'sm' | 'md' | 'lg';
  showWordmark?: boolean;
}

export const Logo: React.FC<LogoProps> = ({
  size = 'md',
  showWordmark = true,
  className,
  ...props
}) => {
  const sizeMap = {
    sm: { icon: 'h-4 w-4', text: 'text-sm font-semibold tracking-tight', gap: 'gap-1.5' },
    md: { icon: 'h-6 w-6', text: 'text-base font-semibold tracking-tight', gap: 'gap-2' },
    lg: { icon: 'h-8 w-8', text: 'text-xl font-bold tracking-tight', gap: 'gap-2.5' },
  };

  const currentSize = sizeMap[size];

  return (
    <div
      className={cn('inline-flex items-center select-none', currentSize.gap, className)}
      {...props}
    >
      <svg
        role="img"
        aria-label="GitContext Logo"
        viewBox="0 0 32 32"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className={cn('shrink-0 text-primary', currentSize.icon)}
      >
        <title>GitContext Logo</title>
        {/* Main commit trunk */}
        <line
          x1="10"
          y1="8"
          x2="10"
          y2="24"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
        {/* Branch curve to feature node */}
        <path
          d="M10 20C10 16 22 16 22 12"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
        {/* Branch / commit nodes */}
        <circle cx="10" cy="8" r="3.25" fill="var(--background)" stroke="currentColor" strokeWidth="2.5" />
        <circle cx="10" cy="24" r="3.25" fill="var(--background)" stroke="currentColor" strokeWidth="2.5" />
        <circle cx="22" cy="12" r="3.25" fill="currentColor" />
      </svg>
      {showWordmark && (
        <span className={cn('text-foreground font-sans', currentSize.text)}>
          Git<span className="text-primary font-bold">Context</span>
        </span>
      )}
    </div>
  );
};
