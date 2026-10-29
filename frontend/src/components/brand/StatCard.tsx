import * as React from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { cn } from '@/lib/utils';

export interface StatCardProps extends React.HTMLAttributes<HTMLDivElement> {
  value: string | number;
  label: string;
  sublabel?: string;
  icon?: React.ReactNode;
}

export const StatCard: React.FC<StatCardProps> = ({
  value,
  label,
  sublabel,
  icon,
  className,
  ...props
}) => {
  return (
    <Card
      className={cn('relative overflow-hidden transition-all duration-200 hover:border-border/80', className)}
      {...props}
    >
      <CardContent className="p-6 flex flex-col justify-between h-full">
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {label}
          </span>
          {icon && <span className="text-muted-foreground shrink-0">{icon}</span>}
        </div>
        <div className="mt-3">
          <div className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground font-mono">
            {value}
          </div>
          {sublabel && (
            <p className="mt-1 text-xs text-muted-foreground">
              {sublabel}
            </p>
          )}
        </div>
      </CardContent>
    </Card>
  );
};
