export type RouteName = 'start' | 'chat' | 'repos' | 'styleguide';

export interface RouteInfo {
  route: RouteName;
  chatId?: string;
  repo?: string;
}

/**
 * Parses URL hash into route, params, and query parameters.
 * Examples:
 *   # -> { route: 'start' }
 *   #/ -> { route: 'start' }
 *   #/?repo=owner/repo -> { route: 'start', repo: 'owner/repo' }
 *   #/c/chat-123 -> { route: 'chat', chatId: 'chat-123' }
 *   #/repos -> { route: 'repos' }
 *   #/styleguide -> { route: 'styleguide' }
 */
export function parseHash(hash: string): RouteInfo {
  const clean = hash.replace(/^#\/?/, '');
  const [pathWithQuery, queryString] = clean.split('?');
  const params = new URLSearchParams(queryString || '');
  const repo = params.get('repo') || undefined;

  const segments = pathWithQuery.split('/').filter(Boolean);
  const first = segments[0] || '';

  if (first === 'repos') {
    return { route: 'repos', repo };
  }
  if (first === 'styleguide') {
    return { route: 'styleguide', repo };
  }
  if (first === 'c' && segments[1]) {
    return { route: 'chat', chatId: segments[1], repo };
  }
  // Backwards compatibility for #/ask
  if (first === 'ask') {
    return { route: 'start', repo };
  }
  return { route: 'start', repo };
}

export function buildChatHash(chatId: string): string {
  return `#/c/${chatId}`;
}

export function buildReposHash(repo?: string): string {
  return repo ? `#/repos?repo=${encodeURIComponent(repo)}` : '#/repos';
}

export function buildStartHash(repo?: string): string {
  return repo ? `#/?repo=${encodeURIComponent(repo)}` : '#/';
}
