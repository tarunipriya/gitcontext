/**
 * Returns the URL string only if new URL(url) has protocol "https:" or "http:", otherwise null.
 * Rejects non-HTTP schemes (e.g., javascript:, data:, file:) and malformed URLs.
 */
export function safeHttpUrl(url?: string | null): string | null {
  if (!url || typeof url !== 'string') {
    return null;
  }
  const trimmed = url.trim();
  if (!trimmed) {
    return null;
  }
  try {
    const parsed = new URL(trimmed);
    if (parsed.protocol === 'https:' || parsed.protocol === 'http:') {
      return parsed.href;
    }
    return null;
  } catch {
    return null;
  }
}
