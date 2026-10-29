import React, { useState, useEffect, lazy, Suspense } from 'react';
import { AskPage } from './components/AskPage';
import { ReposPage } from './components/ReposPage';
import { ThemeProvider } from './components/theme';
import { parseHash, type RouteInfo } from './utils/router';

const StyleGuidePage = lazy(() => import('./components/StyleGuidePage'));

export const App: React.FC = () => {
  const [routeInfo, setRouteInfo] = useState<RouteInfo>(() => parseHash(window.location.hash));

  useEffect(() => {
    const handleHashChange = () => {
      setRouteInfo(parseHash(window.location.hash));
    };

    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  const handleAskRepo = (repo: string) => {
    window.location.hash = `#/ask?repo=${encodeURIComponent(repo)}`;
  };

  let pageContent: React.ReactNode;
  if (routeInfo.route === 'styleguide') {
    pageContent = (
      <Suspense
        fallback={
          <div className="min-h-screen bg-background flex items-center justify-center p-8 text-muted-foreground font-mono text-sm">
            Loading design system style guide...
          </div>
        }
      >
        <StyleGuidePage />
      </Suspense>
    );
  } else if (routeInfo.route === 'repos') {
    pageContent = <ReposPage onAskRepo={handleAskRepo} />;
  } else {
    pageContent = <AskPage initialRepo={routeInfo.repo} />;
  }

  return (
    <ThemeProvider>
      <div className="app-root">
        {pageContent}
      </div>
    </ThemeProvider>
  );
};

export default App;
