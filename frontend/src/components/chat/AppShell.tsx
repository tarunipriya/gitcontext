import React, { useState } from 'react';
import { Menu, Plus } from 'lucide-react';
import { Sidebar } from './Sidebar';
import { Logo } from '../brand/Logo';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetTitle } from '@/components/ui/sheet';
import type { Chat } from '../../types/chat';

export interface AppShellProps {
  children: React.ReactNode;
  activeChatId?: string;
  chats: Chat[];
  onSelectChat: (chatId: string) => void;
  onNewChat: () => void;
  onRenameChat: (chatId: string, newTitle: string) => void;
  onDeleteChat: (chatId: string) => void;
  onNavigateRepos: () => void;
  onNavigateStyleGuide?: () => void;
}

export const AppShell: React.FC<AppShellProps> = ({
  children,
  activeChatId,
  chats,
  onSelectChat,
  onNewChat,
  onRenameChat,
  onDeleteChat,
  onNavigateRepos,
  onNavigateStyleGuide,
}) => {
  const [isCollapsed, setIsCollapsed] = useState<boolean>(false);
  const [isMobileOpen, setIsMobileOpen] = useState<boolean>(false);

  const handleMobileSelectChat = (chatId: string) => {
    setIsMobileOpen(false);
    onSelectChat(chatId);
  };

  const handleMobileNewChat = () => {
    setIsMobileOpen(false);
    onNewChat();
  };

  const handleMobileNavigateRepos = () => {
    setIsMobileOpen(false);
    onNavigateRepos();
  };

  const handleMobileNavigateStyleGuide = () => {
    setIsMobileOpen(false);
    if (onNavigateStyleGuide) onNavigateStyleGuide();
  };

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-background text-foreground">
      {/* Desktop Left Sidebar (hidden on <768px) */}
      <div className="hidden md:flex h-full shrink-0">
        <Sidebar
          isCollapsed={isCollapsed}
          onToggleCollapse={() => setIsCollapsed((prev) => !prev)}
          activeChatId={activeChatId}
          chats={chats}
          onSelectChat={onSelectChat}
          onNewChat={onNewChat}
          onRenameChat={onRenameChat}
          onDeleteChat={onDeleteChat}
          onNavigateRepos={onNavigateRepos}
          onNavigateStyleGuide={onNavigateStyleGuide}
        />
      </div>

      {/* Mobile Sidebar Sheet (<768px) */}
      <Sheet open={isMobileOpen} onOpenChange={setIsMobileOpen}>
        <SheetContent
          side="left"
          className="p-0 w-72 max-w-[85vw] border-r border-border bg-card overflow-hidden"
          aria-label="Navigation drawer"
        >
          <SheetTitle className="sr-only">Navigation Drawer</SheetTitle>
          <Sidebar
            isCollapsed={false}
            onToggleCollapse={() => setIsMobileOpen(false)}
            activeChatId={activeChatId}
            chats={chats}
            onSelectChat={handleMobileSelectChat}
            onNewChat={handleMobileNewChat}
            onRenameChat={onRenameChat}
            onDeleteChat={onDeleteChat}
            onNavigateRepos={handleMobileNavigateRepos}
            onNavigateStyleGuide={handleMobileNavigateStyleGuide}
            className="w-full border-r-0"
          />
        </SheetContent>
      </Sheet>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 h-full overflow-hidden">
        {/* Mobile Top Header (<768px) */}
        <header className="md:hidden flex items-center justify-between px-3 py-2.5 border-b border-border bg-card shrink-0">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={() => setIsMobileOpen(true)}
            aria-label="Open menu"
            className="h-8 w-8 text-foreground"
          >
            <Menu className="h-5 w-5" />
          </Button>

          <a href="#/" onClick={(e) => { e.preventDefault(); onNewChat(); }} className="flex items-center">
            <Logo size="sm" showWordmark={true} />
          </a>

          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={onNewChat}
            aria-label="New chat"
            className="h-8 w-8 text-foreground"
          >
            <Plus className="h-4 w-4" />
          </Button>
        </header>

        {/* Dynamic page content */}
        <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
          {children}
        </div>
      </div>
    </div>
  );
};
