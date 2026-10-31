import { describe, it, expect, beforeEach } from 'vitest';
import {
  loadChats,
  saveChats,
  upsertChat,
  deleteChatById,
  renameChatById,
  getLastRepo,
  setLastRepo,
  MAX_CHATS,
  DEFAULT_REPO,
  CHATS_STORAGE_KEY,
} from './storage';
import type { Chat } from '../types/chat';

describe('storage utility', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('loads empty array when storage is empty or invalid JSON', () => {
    expect(loadChats()).toEqual([]);
    localStorage.setItem(CHATS_STORAGE_KEY, '{ invalid json');
    expect(loadChats()).toEqual([]);
  });

  it('saves and upserts chats correctly', () => {
    const chat: Chat = {
      id: 'chat-1',
      title: 'First Chat',
      repo: 'owner/repo',
      messages: [],
      createdAt: '2026-10-31T10:00:00Z',
      updatedAt: '2026-10-31T10:00:00Z',
    };

    upsertChat(chat);
    expect(loadChats()).toHaveLength(1);
    expect(loadChats()[0].title).toBe('First Chat');

    // Update existing chat
    const updated: Chat = {
      ...chat,
      title: 'Renamed Chat',
    };
    upsertChat(updated);
    expect(loadChats()).toHaveLength(1);
    expect(loadChats()[0].title).toBe('Renamed Chat');
  });

  it('caps chats at 50 and drops the oldest chats', () => {
    const list: Chat[] = [];
    for (let i = 0; i < 60; i++) {
      list.push({
        id: `chat-${i}`,
        title: `Chat ${i}`,
        repo: 'owner/repo',
        messages: [],
        createdAt: new Date(Date.now() - (60 - i) * 60000).toISOString(),
        updatedAt: new Date(Date.now() - (60 - i) * 60000).toISOString(),
      });
    }

    saveChats(list);
    const loaded = loadChats();
    expect(loaded).toHaveLength(MAX_CHATS);
    expect(loaded[0].id).toBe('chat-59'); // Newest
  });

  it('renames and deletes chats', () => {
    const chat1: Chat = {
      id: 'c1',
      title: 'Title 1',
      repo: 'repo',
      messages: [],
      createdAt: '2026-10-31T10:00:00Z',
      updatedAt: '2026-10-31T10:00:00Z',
    };
    const chat2: Chat = {
      id: 'c2',
      title: 'Title 2',
      repo: 'repo',
      messages: [],
      createdAt: '2026-10-31T10:05:00Z',
      updatedAt: '2026-10-31T10:05:00Z',
    };

    saveChats([chat1, chat2]);

    renameChatById('c1', 'New Title');
    expect(loadChats().find((c) => c.id === 'c1')?.title).toBe('New Title');

    deleteChatById('c2');
    expect(loadChats()).toHaveLength(1);
    expect(loadChats()[0].id).toBe('c1');
  });

  it('gets and sets last repository', () => {
    expect(getLastRepo()).toBe(DEFAULT_REPO);
    setLastRepo('custom/repo');
    expect(getLastRepo()).toBe('custom/repo');
  });
});
