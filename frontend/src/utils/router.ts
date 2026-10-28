export interface RouteInfo {
  route: 'ask' | 'repos';
  repo?: string;
}

/**
 * Parses URL hash into route and query parameters (e.g. #/ask?repo=owner/name).
 */
export function parseHash(hash: string): RouteInfo {
  const clean = hash.replace(/^#\/?/, '');
  const [path, queryString] = clean.split('?');
  const params = new URLSearchParams(queryString || '');
  const repo = params.get('repo') || undefined;

  if (path === 'repos') {
    return { route: 'repos', repo };
  }
  return { route: 'ask', repo };
}
