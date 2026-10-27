import React from 'react';

export interface AppHeaderProps {
  currentTab: 'ask' | 'repos';
}

export const AppHeader: React.FC<AppHeaderProps> = ({ currentTab }) => {
  return (
    <header className="ask-header" role="banner">
      <div className="brand">
        <div className="brand-logo" aria-hidden="true">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
            <path d="M9 18c-4.51 2-5-2-7-2" />
          </svg>
        </div>
        <div>
          <h1 className="brand-title">GitContext</h1>
          <p className="brand-subtitle">Grounded repository question answering with verified citations</p>
        </div>
      </div>

      <nav className="header-nav" aria-label="Main Navigation">
        <a
          id="nav-ask"
          href="#/ask"
          className={`nav-tab ${currentTab === 'ask' ? 'active' : ''}`}
          aria-current={currentTab === 'ask' ? 'page' : undefined}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
          <span>Ask</span>
        </a>
        <a
          id="nav-repos"
          href="#/repos"
          className={`nav-tab ${currentTab === 'repos' ? 'active' : ''}`}
          aria-current={currentTab === 'repos' ? 'page' : undefined}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
          </svg>
          <span>Repositories</span>
        </a>
      </nav>
    </header>
  );
};
