import React, { useState, useEffect } from 'react';
import { AskPage } from './components/AskPage';
import { ReposPage } from './components/ReposPage';
import { parseHash, type RouteInfo } from './utils/router';

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

  return (
    <div className="app-root">
      {routeInfo.route === 'repos' ? (
        <ReposPage onAskRepo={handleAskRepo} />
      ) : (
        <AskPage initialRepo={routeInfo.repo} />
      )}
    </div>
  );
};

export default App;
