import * as React from 'react';
import { FileCode, FileText, GitCommit, GitPullRequest, CircleDot } from 'lucide-react';
import { cn } from '@/lib/utils';

export type SourceType = 'CODE' | 'DOC' | 'COMMIT' | 'PULL_REQUEST' | 'ISSUE';

export interface SourceTypeBadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  type: SourceType | string;
  showIcon?: boolean;
}

const configMap: Record<
  string,
  { label: string; icon: React.ComponentType<{ className?: string }>; style: string }
> = {
  CODE: {
    label: 'Code',
    icon: FileCode,
    style: 'bg-sky-500/10 text-sky-400 border-sky-500/20 hover:bg-sky-500/15',
  },
  DOC: {
    label: 'Doc',
    icon: FileText,
    style: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20 hover:bg-emerald-500/15',
  },
  MARKDOWN: {
    label: 'Doc',
    icon: FileText,
    style: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20 hover:bg-emerald-500/15',
  },
  COMMIT: {
    label: 'Commit',
    icon: GitCommit,
    style: 'bg-amber-500/10 text-amber-400 border-amber-500/20 hover:bg-amber-500/15',
  },
  PULL_REQUEST: {
    label: 'PR',
    icon: GitPullRequest,
    style: 'bg-violet-500/10 text-violet-400 border-violet-500/20 hover:bg-violet-500/15',
  },
  ISSUE: {
    label: 'Issue',
    icon: CircleDot,
    style: 'bg-rose-500/10 text-rose-400 border-rose-500/20 hover:bg-rose-500/15',
  },
};

export const SourceTypeBadge: React.FC<SourceTypeBadgeProps> = ({
  type,
  showIcon = true,
  className,
  ...props
}) => {
  const normalizedKey = type.toUpperCase();
  const config = configMap[normalizedKey] || {
    label: type,
    icon: FileCode,
    style: 'bg-muted text-muted-foreground border-border',
  };

  const Icon = config.icon;

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 text-xs font-medium font-mono tracking-tight transition-colors',
        config.style,
        className
      )}
      {...props}
    >
      {showIcon && <Icon className="h-3.5 w-3.5 shrink-0" />}
      <span>{config.label}</span>
    </span>
  );
};
