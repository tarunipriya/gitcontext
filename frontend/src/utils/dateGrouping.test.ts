import { describe, it, expect } from 'vitest';
import { groupChatsByDate } from './dateGrouping';
import type { Chat } from '../types/chat';

describe('groupChatsByDate', () => {
  const refDate = new Date('2026-10-31T12:00:00Z');

  it('groups chats into Today, Yesterday, Previous 7 days, and Older', () => {
    const chats: Chat[] = [
      {
        id: 'c1',
        title: 'Today Chat',
        repo: 'repo',
        messages: [],
        createdAt: '2026-10-31T08:00:00Z',
        updatedAt: '2026-10-31T08:00:00Z',
      },
      {
        id: 'c2',
        title: 'Yesterday Chat',
        repo: 'repo',
        messages: [],
        createdAt: '2026-10-30T15:00:00Z',
        updatedAt: '2026-10-30T15:00:00Z',
      },
      {
        id: 'c3',
        title: '3 Days Ago Chat',
        repo: 'repo',
        messages: [],
        createdAt: '2026-10-28T10:00:00Z',
        updatedAt: '2026-10-28T10:00:00Z',
      },
      {
        id: 'c4',
        title: 'Older Chat',
        repo: 'repo',
        messages: [],
        createdAt: '2026-10-10T10:00:00Z',
        updatedAt: '2026-10-10T10:00:00Z',
      },
    ];

    const groups = groupChatsByDate(chats, refDate);

    expect(groups).toHaveLength(4);
    expect(groups[0].label).toBe('Today');
    expect(groups[0].chats[0].title).toBe('Today Chat');

    expect(groups[1].label).toBe('Yesterday');
    expect(groups[1].chats[0].title).toBe('Yesterday Chat');

    expect(groups[2].label).toBe('Previous 7 days');
    expect(groups[2].chats[0].title).toBe('3 Days Ago Chat');

    expect(groups[3].label).toBe('Older');
    expect(groups[3].chats[0].title).toBe('Older Chat');
  });

  it('omits empty groups when no chats fall into that timeframe', () => {
    const chats: Chat[] = [
      {
        id: 'c1',
        title: 'Today Only',
        repo: 'repo',
        messages: [],
        createdAt: '2026-10-31T09:00:00Z',
        updatedAt: '2026-10-31T09:00:00Z',
      },
    ];

    const groups = groupChatsByDate(chats, refDate);
    expect(groups).toHaveLength(1);
    expect(groups[0].label).toBe('Today');
  });
});
