import React from 'react';
import { Sun, Moon, Laptop } from 'lucide-react';
import { useTheme, type Theme } from './ThemeProvider';
import { cn } from '@/lib/utils';

interface ThemeToggleProps {
  className?: string;
}

export const ThemeToggle: React.FC<ThemeToggleProps> = ({ className }) => {
  const { theme, setTheme } = useTheme();

  const options: { value: Theme; label: string; icon: React.ReactNode }[] = [
    { value: 'light', label: 'Light', icon: <Sun className="h-4 w-4" /> },
    { value: 'dark', label: 'Dark', icon: <Moon className="h-4 w-4" /> },
    { value: 'system', label: 'System', icon: <Laptop className="h-4 w-4" /> },
  ];

  return (
    <div
      role="group"
      aria-label="Theme selection"
      className={cn(
        'inline-flex items-center rounded-lg border border-border bg-muted/60 p-1 backdrop-blur-xs',
        className
      )}
    >
      {options.map((opt) => {
        const isSelected = theme === opt.value;
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => setTheme(opt.value)}
            aria-pressed={isSelected}
            aria-label={`${opt.label} theme`}
            title={`${opt.label} theme`}
            className={cn(
              'inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-all duration-150',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1',
              isSelected
                ? 'bg-card text-foreground shadow-xs'
                : 'text-muted-foreground hover:text-foreground hover:bg-card/50'
            )}
          >
            {opt.icon}
            <span className="capitalize">{opt.label}</span>
          </button>
        );
      })}
    </div>
  );
};
