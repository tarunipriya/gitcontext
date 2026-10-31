import React, { useState } from 'react';
import {
  Plus,
  MessageSquare,
  FolderGit2,
  Palette,
  PanelLeftClose,
  PanelLeftOpen,
  MoreHorizontal,
  Edit2,
  Trash2,
  Check,
  X,
} from 'lucide-react';
import { Logo } from '../brand/Logo';
import { ThemeToggle } from '../theme/ThemeToggle';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { groupChatsByDate } from '../../utils/dateGrouping';
import { cn } from '@/lib/utils';
import type { Chat } from '../../types/chat';

export interface SidebarProps {
  isCollapsed: boolean;
  onToggleCollapse: () => void;
  activeChatId?: string;
  chats: Chat[];
  onSelectChat: (chatId: string) => void;
  onNewChat: () => void;
  onRenameChat: (chatId: string, newTitle: string) => void;
  onDeleteChat: (chatId: string) => void;
  onNavigateRepos: () => void;
  onNavigateStyleGuide?: () => void;
  className?: string;
}

export const Sidebar: React.FC<SidebarProps> = ({
  isCollapsed,
  onToggleCollapse,
  activeChatId,
  chats,
  onSelectChat,
  onNewChat,
  onRenameChat,
  onDeleteChat,
  onNavigateRepos,
  onNavigateStyleGuide,
  className,
}) => {
  const [editingChatId, setEditingChatId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState<string>('');

  const chatGroups = groupChatsByDate(chats);
  const isDev = Boolean((import.meta as unknown as { env?: { DEV?: boolean } }).env?.DEV);

  const startRenaming = (chat: Chat, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    setEditingChatId(chat.id);
    setEditTitle(chat.title);
  };

  const confirmRename = (chatId: string, e?: React.FormEvent | React.MouseEvent) => {
    if (e) e.stopPropagation();
    if (editTitle.trim().length > 0) {
      onRenameChat(chatId, editTitle.trim());
    }
    setEditingChatId(null);
  };

  const cancelRename = (e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    setEditingChatId(null);
  };

  return (
    <aside
      className={cn(
        'h-full flex flex-col bg-card border-r border-border select-none transition-all duration-200 overflow-hidden shrink-0 z-30',
        isCollapsed ? 'w-16' : 'w-64',
        className
      )}
      aria-label="Sidebar navigation"
    >
      {/* Top Header */}
      <div className="p-3 border-b border-border/40 flex flex-col gap-3 shrink-0">
        <div className={cn('flex items-center', isCollapsed ? 'justify-center' : 'justify-between px-1')}>
          <a
            href="#/"
            onClick={(e) => {
              e.preventDefault();
              onNewChat();
            }}
            className="flex items-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-lg p-0.5"
            aria-label="GitContext Home"
          >
            <Logo size="md" showWordmark={!isCollapsed} />
          </a>
        </div>

        {/* New chat button */}
        <Button
          type="button"
          onClick={onNewChat}
          size={isCollapsed ? 'icon' : 'sm'}
          aria-label="New chat"
          className={cn(
            'rounded-xl border border-border/80 bg-muted/50 hover:bg-muted text-foreground transition-all shadow-2xs font-medium',
            isCollapsed ? 'h-9 w-9 mx-auto' : 'w-full justify-start px-3 h-9 text-xs gap-2'
          )}
        >
          <Plus className="h-4 w-4 text-primary shrink-0" />
          {!isCollapsed && <span>New chat</span>}
        </Button>
      </div>

      {/* Middle: Recent Chats grouped by date */}
      <div className="flex-1 overflow-y-auto px-2 py-3 space-y-4">
        {chats.length === 0 ? (
          !isCollapsed && (
            <div className="px-3 py-6 text-center text-xs text-muted-foreground/60">
              No recent conversations
            </div>
          )
        ) : isCollapsed ? (
          <div className="space-y-1">
            {chats.slice(0, 15).map((chat) => (
              <button
                key={chat.id}
                type="button"
                onClick={() => onSelectChat(chat.id)}
                aria-label={chat.title}
                title={chat.title}
                className={cn(
                  'w-9 h-9 mx-auto flex items-center justify-center rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted transition-colors cursor-pointer',
                  activeChatId === chat.id && 'bg-primary/15 text-primary'
                )}
              >
                <MessageSquare className="h-4 w-4" />
              </button>
            ))}
          </div>
        ) : (
          chatGroups.map((group) => (
            <div key={group.label} className="space-y-1">
              <h3 className="text-[11px] font-semibold text-muted-foreground/70 uppercase tracking-wider px-2 py-1 select-none">
                {group.label}
              </h3>

              <div className="space-y-0.5" role="list">
                {group.chats.map((chat) => {
                  const isActive = activeChatId === chat.id;
                  const isEditing = editingChatId === chat.id;

                  if (isEditing) {
                    return (
                      <div
                        key={chat.id}
                        className="flex items-center gap-1 px-2 py-1 rounded-lg bg-muted border border-border"
                      >
                        <input
                          type="text"
                          value={editTitle}
                          onChange={(e) => setEditTitle(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') confirmRename(chat.id, e);
                            if (e.key === 'Escape') cancelRename();
                          }}
                          className="h-6 w-full text-xs bg-transparent border-0 focus:outline-none text-foreground p-0"
                          autoFocus
                          aria-label="Rename chat title"
                        />
                        <button
                          type="button"
                          onClick={(e) => confirmRename(chat.id, e)}
                          aria-label="Save title"
                          className="p-1 text-primary hover:text-primary/80"
                        >
                          <Check className="h-3 w-3" />
                        </button>
                        <button
                          type="button"
                          onClick={cancelRename}
                          aria-label="Cancel rename"
                          className="p-1 text-muted-foreground hover:text-foreground"
                        >
                          <X className="h-3 w-3" />
                        </button>
                      </div>
                    );
                  }

                  return (
                    <div
                      key={chat.id}
                      role="listitem"
                      className={cn(
                        'group flex items-center justify-between rounded-lg px-2 py-1.5 text-xs text-muted-foreground hover:text-foreground hover:bg-muted/70 transition-colors cursor-pointer',
                        isActive && 'bg-accent text-accent-foreground font-medium'
                      )}
                      onClick={() => onSelectChat(chat.id)}
                    >
                      <span className="truncate pr-1 text-left flex-1" title={chat.title}>
                        {chat.title}
                      </span>

                      {/* Three-dots menu */}
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <button
                            type="button"
                            onClick={(e) => e.stopPropagation()}
                            className="opacity-0 group-hover:opacity-100 focus:opacity-100 p-1 rounded hover:bg-muted text-muted-foreground hover:text-foreground transition-opacity"
                            aria-label={`Options for ${chat.title}`}
                          >
                            <MoreHorizontal className="h-3.5 w-3.5" />
                          </button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-36">
                          <DropdownMenuItem
                            onClick={(e) => startRenaming(chat, e)}
                            className="text-xs gap-2 cursor-pointer"
                          >
                            <Edit2 className="h-3.5 w-3.5" />
                            <span>Rename</span>
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={(e) => {
                              e.stopPropagation();
                              onDeleteChat(chat.id);
                            }}
                            className="text-xs gap-2 text-destructive focus:text-destructive cursor-pointer"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                            <span>Delete</span>
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </div>
                  );
                })}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Bottom Area */}
      <div className="p-2 border-t border-border/40 flex flex-col gap-1 shrink-0 bg-muted/20">
        {/* Repositories Link */}
        <Button
          type="button"
          variant="ghost"
          size={isCollapsed ? 'icon' : 'sm'}
          onClick={onNavigateRepos}
          aria-label="Repositories"
          className={cn(
            'text-xs text-muted-foreground hover:text-foreground justify-start',
            isCollapsed ? 'h-9 w-9 mx-auto' : 'w-full px-2 gap-2 h-8'
          )}
        >
          <FolderGit2 className="h-4 w-4 shrink-0" />
          {!isCollapsed && <span>Repositories</span>}
        </Button>

        {/* Style Guide Link (Dev Only) */}
        {isDev && onNavigateStyleGuide && (
          <Button
            type="button"
            variant="ghost"
            size={isCollapsed ? 'icon' : 'sm'}
            onClick={onNavigateStyleGuide}
            aria-label="Style guide"
            className={cn(
              'text-xs text-muted-foreground hover:text-foreground justify-start',
              isCollapsed ? 'h-9 w-9 mx-auto' : 'w-full px-2 gap-2 h-8'
            )}
          >
            <Palette className="h-4 w-4 shrink-0" />
            {!isCollapsed && <span>Style guide</span>}
          </Button>
        )}

        <div className={cn('flex items-center pt-1 border-t border-border/30', isCollapsed ? 'flex-col gap-2' : 'justify-between px-1')}>
          <ThemeToggle />

          {/* Collapse/Expand button */}
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={onToggleCollapse}
            aria-label={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            className="h-8 w-8 text-muted-foreground hover:text-foreground"
          >
            {isCollapsed ? (
              <PanelLeftOpen className="h-4 w-4" />
            ) : (
              <PanelLeftClose className="h-4 w-4" />
            )}
          </Button>
        </div>
      </div>
    </aside>
  );
};
