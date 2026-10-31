import * as React from 'react';
import { Toaster as Sonner, toast } from 'sonner';
import { ThemeContext } from '@/components/theme';

type ToasterProps = React.ComponentProps<typeof Sonner>;

const Toaster = ({ ...props }: ToasterProps) => {
  const themeContext = React.useContext(ThemeContext);
  const resolvedTheme = themeContext?.resolvedTheme || 'dark';

  return (
    <Sonner
      theme={resolvedTheme as ToasterProps['theme']}
      className="toaster group"
      toastOptions={{
        classNames: {
          toast:
            'group toast group-[.toaster]:bg-card group-[.toaster]:text-card-foreground group-[.toaster]:border-border group-[.toaster]:shadow-lg',
          description: 'group-[.toast]:text-muted-foreground',
          actionButton:
            'group-[.toast]:bg-primary group-[.toast]:text-primary-foreground',
          cancelButton:
            'group-[.toast]:bg-muted group-[.toast]:text-muted-foreground',
        },
      }}
      {...props}
    />
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export { Toaster, toast };
