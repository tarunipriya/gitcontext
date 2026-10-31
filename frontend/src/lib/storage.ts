import type { Chat } from '../types/chat';

export const CHATS_STORAGE_KEY = 'gitcontext-chats';
export const LAST_REPO_STORAGE_KEY = 'gitcontext:last_repo';
export const DEFAULT_REPO = 'tarunipriya/gitcontext';
export const MAX_CHATS = 50;

/**
 * Loads chats from localStorage.
 * Returns an empty array if storage is empty, restricted, or corrupted.
 */
export function loadChats(): Chat[] {
  try {
    if (typeof localStorage === 'undefined') return [];
    const raw = localStorage.getItem(CHATS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed;
  } catch {
    return [];
  }
}

/**
 * Saves chats to localStorage, capped at MAX_CHATS (50).
 * Drops oldest chats when exceeding the cap.
 */
export function saveChats(chats: Chat[]): void {
  try {
    if (typeof localStorage === 'undefined') return;
    // Sort by updatedAt/createdAt descending and take the newest MAX_CHATS
    const sorted = [...chats].sort((a, b) => {
      const timeA = new Date(a.updatedAt || a.createdAt).getTime();
      const timeB = new Date(b.updatedAt || b.createdAt).getTime();
      return timeB - timeA;
    });
    const capped = sorted.slice(0, MAX_CHATS);
    localStorage.setItem(CHATS_STORAGE_KEY, JSON.stringify(capped));
  } catch {
    // Gracefully handle storage quota or security errors
  }
}

/**
 * Retrieves a single chat by its ID.
 */
export function getChatById(id: string): Chat | undefined {
  const chats = loadChats();
  return chats.find((c) => c.id === id);
}

/**
 * Upserts a chat into localStorage.
 */
export function upsertChat(chat: Chat): void {
  const current = loadChats();
  const existingIdx = current.findIndex((c) => c.id === chat.id);
  let updated: Chat[];
  if (existingIdx >= 0) {
    updated = [...current];
    updated[existingIdx] = chat;
  } else {
    updated = [chat, ...current];
  }
  saveChats(updated);
}

/**
 * Deletes a chat from localStorage by its ID.
 */
export function deleteChatById(id: string): Chat[] {
  const current = loadChats();
  const next = current.filter((c) => c.id !== id);
  saveChats(next);
  return next;
}

/**
 * Renames a chat title in localStorage.
 */
export function renameChatById(id: string, newTitle: string): Chat[] {
  const trimmed = newTitle.trim();
  if (!trimmed) return loadChats();
  const current = loadChats();
  const next = current.map((c) => (c.id === id ? { ...c, title: trimmed, updatedAt: new Date().toISOString() } : c));
  saveChats(next);
  return next;
}

/**
 * Retrieves the last selected repository.
 */
export function getLastRepo(): string {
  try {
    if (typeof localStorage === 'undefined') return DEFAULT_REPO;
    const saved = localStorage.getItem(LAST_REPO_STORAGE_KEY);
    return saved && saved.trim().length > 0 ? saved.trim() : DEFAULT_REPO;
  } catch {
    return DEFAULT_REPO;
  }
}

/**
 * Persists the last selected repository.
 */
export function setLastRepo(repo: string): void {
  try {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(LAST_REPO_STORAGE_KEY, repo.trim());
  } catch {
    // Ignore storage quota
  }
}
