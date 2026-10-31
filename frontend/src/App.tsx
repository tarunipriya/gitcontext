import React, { useState, useEffect, useCallback, useRef, lazy, Suspense } from 'react';
import { AppShell } from './components/chat/AppShell';
import { StartScreen } from './components/chat/StartScreen';
import { ConversationView } from './components/chat/ConversationView';
import { ReposPage } from './components/ReposPage';
import { ThemeProvider } from './components/theme';
import {
  parseHash,
  buildChatHash,
  buildStartHash,
  buildReposHash,
  type RouteInfo,
} from './utils/router';
import {
  loadChats,
  getChatById,
  upsertChat,
  deleteChatById,
  renameChatById,
  getLastRepo,
  setLastRepo,
  DEFAULT_REPO,
} from './lib/storage';
import {
  askRepo,
  getRepos,
  ApiError,
  type RepoSummaryResponse,
  type ProblemDetail,
} from './api/client';
import { parseRepoRef } from './utils/formatters';
import type { Chat, ChatMessage } from './types/chat';

const StyleGuidePage = lazy(() => import('./components/StyleGuidePage'));

export const App: React.FC = () => {
  const [routeInfo, setRouteInfo] = useState<RouteInfo>(() => parseHash(window.location.hash));
  const [chats, setChats] = useState<Chat[]>(() => loadChats());
  const [repos, setRepos] = useState<RepoSummaryResponse[]>([]);
  const [reposLoading, setReposLoading] = useState<boolean>(true);

  const [selectedRepo, setSelectedRepo] = useState<string>(() => {
    const fromUrl = parseHash(window.location.hash).repo;
    if (fromUrl && fromUrl.trim().length > 0) return fromUrl.trim();
    return getLastRepo() || DEFAULT_REPO;
  });

  const [isPending, setIsPending] = useState<boolean>(false);
  const activeAbortControllerRef = useRef<AbortController | null>(null);

  // Fetch indexed repos on mount
  useEffect(() => {
    let mounted = true;
    const fetchRepos = async () => {
      setReposLoading(true);
      try {
        const data = await getRepos();
        if (mounted) {
          setRepos(data);
          if (data.length > 0) {
            // If current selectedRepo is not among loaded repos and no repo was explicitly set in URL
            const urlRepo = parseHash(window.location.hash).repo;
            if (!urlRepo) {
              setSelectedRepo((current) => {
                const match = data.find((r) => r.repo === current);
                if (!match && data[0]) {
                  setLastRepo(data[0].repo);
                  return data[0].repo;
                }
                return current;
              });
            }
          }
        }
      } catch {
        // Handled gracefully
      } finally {
        if (mounted) setReposLoading(false);
      }
    };
    fetchRepos();
    return () => {
      mounted = false;
    };
  }, []);

  // Listen to hash changes
  useEffect(() => {
    const handleHashChange = () => {
      const parsed = parseHash(window.location.hash);
      setRouteInfo(parsed);
      if (parsed.repo && parsed.repo.trim().length > 0) {
        setSelectedRepo(parsed.repo.trim());
        setLastRepo(parsed.repo.trim());
      }
    };

    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  // Sync selected repository changes
  const handleSelectRepo = (repo: string) => {
    setSelectedRepo(repo);
    setLastRepo(repo);
  };

  // Chat navigation
  const handleSelectChat = (chatId: string) => {
    window.location.hash = buildChatHash(chatId);
    setRouteInfo({ route: 'chat', chatId, repo: selectedRepo });
  };

  const handleNewChat = () => {
    window.location.hash = buildStartHash(selectedRepo);
    setRouteInfo({ route: 'start', repo: selectedRepo });
  };

  const handleNavigateRepos = () => {
    window.location.hash = buildReposHash(selectedRepo);
    setRouteInfo({ route: 'repos', repo: selectedRepo });
  };

  const handleNavigateStyleGuide = () => {
    window.location.hash = '#/styleguide';
    setRouteInfo({ route: 'styleguide' });
  };

  const handleRenameChat = (chatId: string, newTitle: string) => {
    const updated = renameChatById(chatId, newTitle);
    setChats(updated);
  };

  const handleDeleteChat = (chatId: string) => {
    const updated = deleteChatById(chatId);
    setChats(updated);
    if (routeInfo.route === 'chat' && routeInfo.chatId === chatId) {
      window.location.hash = buildStartHash(selectedRepo);
    }
  };

  // Stop currently running ask request
  const handleStop = useCallback(() => {
    if (activeAbortControllerRef.current) {
      activeAbortControllerRef.current.abort();
      activeAbortControllerRef.current = null;
    }
    setIsPending(false);
  }, []);

  // Core Ask execution logic
  const executeAsk = async (
    targetChatId: string,
    questionText: string,
    assistantMessageId: string,
    targetRepo: string
  ) => {
    const controller = new AbortController();
    activeAbortControllerRef.current = controller;
    setIsPending(true);

    // Dynamic status text transition
    const statusTimer = setTimeout(() => {
      setChats((prev) =>
        prev.map((c) => {
          if (c.id !== targetChatId) return c;
          return {
            ...c,
            messages: c.messages.map((m) =>
              m.id === assistantMessageId ? { ...m, statusText: 'Writing the answer…' } : m
            ),
          };
        })
      );
    }, 1500);

    try {
      const parsed = parseRepoRef(targetRepo) || {
        owner: targetRepo.split('/')[0] || 'tarunipriya',
        name: targetRepo.split('/')[1] || 'gitcontext',
        fullName: targetRepo,
      };

      const res = await askRepo(
        parsed.owner,
        parsed.name,
        { question: questionText, mode: 'HYBRID', excludeTests: true },
        controller.signal
      );

      clearTimeout(statusTimer);

      setChats((prev) => {
        const next = prev.map((c) => {
          if (c.id !== targetChatId) return c;
          const updatedMessages: ChatMessage[] = c.messages.map((m) => {
            if (m.id === assistantMessageId) {
              return {
                ...m,
                pending: false,
                content: res.answer,
                response: res,
                revealed: false,
                statusText: undefined,
              };
            }
            return m;
          });
          const updatedChat: Chat = {
            ...c,
            messages: updatedMessages,
            updatedAt: new Date().toISOString(),
          };
          upsertChat(updatedChat);
          return updatedChat;
        });
        return next;
      });
    } catch (err: unknown) {
      clearTimeout(statusTimer);
      if (controller.signal.aborted) {
        // Aborted by user
        setChats((prev) => {
          const next = prev.map((c) => {
            if (c.id !== targetChatId) return c;
            return {
              ...c,
              messages: c.messages.filter((m) => m.id !== assistantMessageId),
            };
          });
          return next;
        });
        return;
      }

      let problemDetail: ProblemDetail;
      if (err instanceof ApiError) {
        problemDetail = err.problemDetail || {
          title: err.title || 'Error',
          detail: err.detail || err.message,
          status: err.status,
        };
      } else if (err instanceof Error) {
        problemDetail = {
          title: 'Request Failed',
          detail: err.message,
        };
      } else {
        problemDetail = {
          title: 'Unknown Error',
          detail: 'An unexpected error occurred while communicating with the server.',
        };
      }

      setChats((prev) => {
        const next = prev.map((c) => {
          if (c.id !== targetChatId) return c;
          const updatedMessages: ChatMessage[] = c.messages.map((m) => {
            if (m.id === assistantMessageId) {
              return {
                ...m,
                pending: false,
                error: problemDetail,
                statusText: undefined,
              };
            }
            return m;
          });
          const updatedChat: Chat = {
            ...c,
            messages: updatedMessages,
            updatedAt: new Date().toISOString(),
          };
          upsertChat(updatedChat);
          return updatedChat;
        });
        return next;
      });
    } finally {
      activeAbortControllerRef.current = null;
      setIsPending(false);
    }
  };

  // Start a new chat with a question
  const handleStartChat = (questionText: string) => {
    const trimmed = questionText.trim();
    if (!trimmed) return;

    const newChatId = `chat_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
    const userMsgId = `msg_user_${Date.now()}`;
    const asstMsgId = `msg_asst_${Date.now()}`;
    const nowIso = new Date().toISOString();

    const title = trimmed.length > 50 ? `${trimmed.slice(0, 47)}…` : trimmed;

    const userMessage: ChatMessage = {
      id: userMsgId,
      role: 'user',
      content: trimmed,
      createdAt: nowIso,
    };

    const assistantMessage: ChatMessage = {
      id: asstMsgId,
      role: 'assistant',
      content: '',
      createdAt: nowIso,
      pending: true,
      statusText: 'Searching the repository…',
    };

    const newChat: Chat = {
      id: newChatId,
      title,
      repo: selectedRepo,
      messages: [userMessage, assistantMessage],
      createdAt: nowIso,
      updatedAt: nowIso,
    };

    upsertChat(newChat);
    setChats((prev) => [newChat, ...prev]);

    // Change URL hash to the new chat
    window.location.hash = buildChatHash(newChatId);
    setRouteInfo({ route: 'chat', chatId: newChatId, repo: selectedRepo });

    // Launch ask
    executeAsk(newChatId, trimmed, asstMsgId, selectedRepo);
  };

  // Follow-up question inside an active chat
  const handleSendMessage = (questionText: string) => {
    const activeChat = chats.find((c) => c.id === routeInfo.chatId);
    if (!activeChat) {
      handleStartChat(questionText);
      return;
    }

    const trimmed = questionText.trim();
    if (!trimmed) return;

    const userMsgId = `msg_user_${Date.now()}`;
    const asstMsgId = `msg_asst_${Date.now()}`;
    const nowIso = new Date().toISOString();

    const userMessage: ChatMessage = {
      id: userMsgId,
      role: 'user',
      content: trimmed,
      createdAt: nowIso,
    };

    const assistantMessage: ChatMessage = {
      id: asstMsgId,
      role: 'assistant',
      content: '',
      createdAt: nowIso,
      pending: true,
      statusText: 'Searching the repository…',
    };

    const updatedChat: Chat = {
      ...activeChat,
      messages: [...activeChat.messages, userMessage, assistantMessage],
      updatedAt: nowIso,
    };

    upsertChat(updatedChat);
    setChats((prev) => prev.map((c) => (c.id === updatedChat.id ? updatedChat : c)));

    executeAsk(activeChat.id, trimmed, asstMsgId, activeChat.repo || selectedRepo);
  };

  // Retry an assistant response
  const handleRetry = (assistantMessageId: string) => {
    const activeChat = chats.find((c) => c.id === routeInfo.chatId);
    if (!activeChat) return;

    const msgIdx = activeChat.messages.findIndex((m) => m.id === assistantMessageId);
    if (msgIdx === -1) return;

    // Find previous user message
    let questionText = '';
    for (let i = msgIdx - 1; i >= 0; i--) {
      if (activeChat.messages[i].role === 'user') {
        questionText = activeChat.messages[i].content;
        break;
      }
    }
    if (!questionText) return;

    // Mark assistant message as pending
    setChats((prev) =>
      prev.map((c) => {
        if (c.id !== activeChat.id) return c;
        return {
          ...c,
          messages: c.messages.map((m) =>
            m.id === assistantMessageId
              ? {
                  ...m,
                  pending: true,
                  error: undefined,
                  statusText: 'Searching the repository…',
                }
              : m
          ),
        };
      })
    );

    executeAsk(activeChat.id, questionText, assistantMessageId, activeChat.repo || selectedRepo);
  };

  // When clicking "New chat" on a repository in ReposPage
  const handleAskRepoFromReposPage = (repoName: string) => {
    handleSelectRepo(repoName);
    window.location.hash = buildStartHash(repoName);
    setRouteInfo({ route: 'start', repo: repoName });
  };

  // Determine current main view
  const currentChat = routeInfo.chatId
    ? chats.find((c) => c.id === routeInfo.chatId) || getChatById(routeInfo.chatId)
    : undefined;

  let mainContent: React.ReactNode;
  if (routeInfo.route === 'styleguide') {
    mainContent = (
      <Suspense
        fallback={
          <div className="min-h-full flex items-center justify-center p-8 text-muted-foreground font-mono text-sm">
            Loading design system style guide...
          </div>
        }
      >
        <StyleGuidePage />
      </Suspense>
    );
  } else if (routeInfo.route === 'repos') {
    mainContent = <ReposPage onAskRepo={handleAskRepoFromReposPage} />;
  } else if (routeInfo.route === 'chat' && currentChat) {
    mainContent = (
      <ConversationView
        chat={currentChat}
        onSendMessage={handleSendMessage}
        onRetry={handleRetry}
        onStop={handleStop}
        isPending={isPending}
        selectedRepo={currentChat.repo || selectedRepo}
        onRepoChange={handleSelectRepo}
        repos={repos}
        reposLoading={reposLoading}
      />
    );
  } else {
    mainContent = (
      <StartScreen
        selectedRepo={selectedRepo}
        onRepoChange={handleSelectRepo}
        repos={repos}
        reposLoading={reposLoading}
        onStartChat={handleStartChat}
        isPending={isPending}
        onStop={handleStop}
      />
    );
  }

  return (
    <ThemeProvider>
      <AppShell
        activeChatId={routeInfo.chatId}
        chats={chats}
        onSelectChat={handleSelectChat}
        onNewChat={handleNewChat}
        onRenameChat={handleRenameChat}
        onDeleteChat={handleDeleteChat}
        onNavigateRepos={handleNavigateRepos}
        onNavigateStyleGuide={handleNavigateStyleGuide}
      >
        {mainContent}
      </AppShell>
    </ThemeProvider>
  );
};

export default App;
