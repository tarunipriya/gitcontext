import type { Chat } from '../types/chat';

export type DateGroupLabel = 'Today' | 'Yesterday' | 'Previous 7 days' | 'Older';

export interface ChatGroup {
  label: DateGroupLabel;
  chats: Chat[];
}

/**
 * Groups chats into 'Today', 'Yesterday', 'Previous 7 days', and 'Older'.
 * Groups are returned in chronological order, omitting empty groups.
 */
export function groupChatsByDate(chats: Chat[], referenceDate: Date = new Date()): ChatGroup[] {
  if (!chats || chats.length === 0) return [];

  // Start of today (midnight)
  const startOfToday = new Date(referenceDate);
  startOfToday.setHours(0, 0, 0, 0);

  // Start of yesterday (midnight)
  const startOfYesterday = new Date(startOfToday);
  startOfYesterday.setDate(startOfYesterday.getDate() - 1);

  // Start of 7 days ago (midnight)
  const startOf7DaysAgo = new Date(startOfToday);
  startOf7DaysAgo.setDate(startOf7DaysAgo.getDate() - 7);

  const groups: Record<DateGroupLabel, Chat[]> = {
    Today: [],
    Yesterday: [],
    'Previous 7 days': [],
    Older: [],
  };

  for (const chat of chats) {
    const chatDate = new Date(chat.updatedAt || chat.createdAt);
    if (isNaN(chatDate.getTime())) {
      groups.Older.push(chat);
      continue;
    }

    if (chatDate >= startOfToday) {
      groups.Today.push(chat);
    } else if (chatDate >= startOfYesterday) {
      groups.Yesterday.push(chat);
    } else if (chatDate >= startOf7DaysAgo) {
      groups['Previous 7 days'].push(chat);
    } else {
      groups.Older.push(chat);
    }
  }

  const order: DateGroupLabel[] = ['Today', 'Yesterday', 'Previous 7 days', 'Older'];
  return order
    .filter((label) => groups[label].length > 0)
    .map((label) => ({
      label,
      chats: groups[label],
    }));
}
