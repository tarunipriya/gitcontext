import { describe, it, expect } from 'vitest';
import { safeHttpUrl } from './safeUrl';

describe('safeHttpUrl helper', () => {
  it('accepts valid https and http URLs', () => {
    expect(safeHttpUrl('https://github.com/tarunipriya/gitcontext')).toBe(
      'https://github.com/tarunipriya/gitcontext'
    );
    expect(safeHttpUrl('http://localhost:8081/api/health')).toBe(
      'http://localhost:8081/api/health'
    );
  });

  it('rejects dangerous javascript: URLs', () => {
    expect(safeHttpUrl('javascript:alert(1)')).toBeNull();
    expect(safeHttpUrl('JAVASCRIPT:alert(document.cookie)')).toBeNull();
  });

  it('rejects data: URLs', () => {
    expect(safeHttpUrl('data:text/html,<script>alert(1)</script>')).toBeNull();
  });

  it('rejects file: and relative or malformed URLs', () => {
    expect(safeHttpUrl('file:///etc/passwd')).toBeNull();
    expect(safeHttpUrl('/local/path')).toBeNull();
    expect(safeHttpUrl('not a url')).toBeNull();
    expect(safeHttpUrl('')).toBeNull();
    expect(safeHttpUrl(null)).toBeNull();
    expect(safeHttpUrl(undefined)).toBeNull();
  });
});
