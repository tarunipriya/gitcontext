import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  ThemeContext,
  STORAGE_KEY,
  type Theme,
  type ResolvedTheme,
  type ThemeContextValue,
} from './ThemeContext';

// eslint-disable-next-line react-refresh/only-export-components
export { useTheme, ThemeContext, type Theme, type ResolvedTheme, type ThemeContextValue } from './ThemeContext';

export interface ThemeProviderProps {
  children: React.ReactNode;
  defaultTheme?: Theme;
  storageKey?: string;
}

function getStoredTheme(key: string, fallback: Theme): Theme {
  try {
    const item = localStorage.getItem(key);
    if (item === 'dark' || item === 'light' || item === 'system') {
      return item;
    }
  } catch {
    // localStorage might be unavailable/blocked in certain environments
  }
  return fallback;
}

function setStoredTheme(key: string, value: Theme): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // ignore storage write errors
  }
}

function getSystemTheme(): ResolvedTheme {
  if (typeof window === 'undefined' || !window.matchMedia) {
    return 'dark';
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export const ThemeProvider: React.FC<ThemeProviderProps> = ({
  children,
  defaultTheme = 'dark',
  storageKey = STORAGE_KEY,
}) => {
  const [theme, setThemeState] = useState<Theme>(() => getStoredTheme(storageKey, defaultTheme));
  const [systemTheme, setSystemTheme] = useState<ResolvedTheme>(() => getSystemTheme());

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = (e: MediaQueryListEvent) => {
      setSystemTheme(e.matches ? 'dark' : 'light');
    };

    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', handleChange);
      return () => mediaQuery.removeEventListener('change', handleChange);
    } else if (mediaQuery.addListener) {
      // Legacy browsers support
      mediaQuery.addListener(handleChange);
      return () => mediaQuery.removeListener(handleChange);
    }
  }, []);

  const resolvedTheme: ResolvedTheme = useMemo(() => {
    if (theme === 'system') {
      return systemTheme;
    }
    return theme;
  }, [theme, systemTheme]);

  useEffect(() => {
    const root = document.documentElement;
    if (resolvedTheme === 'light') {
      root.classList.add('light');
      root.classList.remove('dark');
    } else {
      root.classList.remove('light');
      root.classList.add('dark');
    }
  }, [resolvedTheme]);

  const setTheme = useCallback(
    (nextTheme: Theme) => {
      setStoredTheme(storageKey, nextTheme);
      setThemeState(nextTheme);
    },
    [storageKey]
  );

  const value: ThemeContextValue = useMemo(
    () => ({
      theme,
      resolvedTheme,
      setTheme,
    }),
    [theme, resolvedTheme, setTheme]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
};
