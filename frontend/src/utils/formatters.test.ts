import { describe, it, expect } from 'vitest';
import {
  parseRepoRef,
  isValidRepoRef,
  formatSourceTitle,
  formatElapsedTime,
  formatRelativeTime,
} from './formatters';

describe('formatters', () => {
  describe('parseRepoRef & isValidRepoRef', () => {
    it('accepts standard owner/name formats', () => {
      expect(parseRepoRef('tarunipriya/gitcontext')).toEqual({
        owner: 'tarunipriya',
        name: 'gitcontext',
        fullName: 'tarunipriya/gitcontext',
      });
      expect(isValidRepoRef('facebook/react')).toBe(true);
      expect(isValidRepoRef('org-name/repo.name_123')).toBe(true);
    });

    it('accepts full GitHub URLs and SSH references', () => {
      expect(parseRepoRef('https://github.com/owner/repo')).toEqual({
        owner: 'owner',
        name: 'repo',
        fullName: 'owner/repo',
      });
      expect(isValidRepoRef('https://github.com/owner/repo.git')).toBe(true);
      expect(isValidRepoRef('http://github.com/owner/repo')).toBe(true);
      expect(isValidRepoRef('git@github.com:owner/repo.git')).toBe(true);
      expect(isValidRepoRef('/owner/repo')).toBe(true);
    });

    it('rejects invalid repository strings', () => {
      expect(isValidRepoRef('')).toBe(false);
      expect(isValidRepoRef('   ')).toBe(false);
      expect(isValidRepoRef('justname')).toBe(false);
      expect(isValidRepoRef('owner/name/extra')).toBe(false);
      expect(isValidRepoRef('owner/name with spaces')).toBe(false);
      expect(isValidRepoRef('owner/')).toBe(false);
      expect(isValidRepoRef('/name')).toBe(false);
    });
  });

  describe('formatSourceTitle', () => {
    it('returns file path when path is present', () => {
      const source = {
        number: 1,
        path: 'src/main/java/Main.java',
        cited: true,
        snippet: 'code',
      };
      expect(formatSourceTitle(source)).toBe('src/main/java/Main.java');
    });

    it('formats commits with short sha and first line of message', () => {
      const source = {
        number: 1,
        path: null,
        chunkType: 'COMMIT',
        url: 'https://github.com/owner/repo/commit/abc1234567890abcdef',
        title: 'feat: implement search endpoint',
        cited: true,
        snippet: 'commit message',
      };
      expect(formatSourceTitle(source)).toBe('commit abc1234 — feat: implement search endpoint');
    });

    it('formats commits with commitSha field if url is absent', () => {
      const source = {
        number: 1,
        path: null,
        chunkType: 'COMMIT',
        commitSha: '9876543210abcdef',
        title: 'Initial commit',
        cited: false,
        snippet: '',
      };
      expect(formatSourceTitle(source)).toBe('commit 9876543 — Initial commit');
    });

    it('formats PRs with PR number and title', () => {
      const source = {
        number: 2,
        path: null,
        chunkType: 'PULL_REQUEST',
        url: 'https://github.com/owner/repo/pull/16',
        title: 'Add hybrid reciprocal rank fusion',
        cited: true,
        snippet: '',
      };
      expect(formatSourceTitle(source)).toBe('PR #16 — Add hybrid reciprocal rank fusion');
    });

    it('formats issues with issue number and title', () => {
      const source = {
        number: 3,
        path: null,
        chunkType: 'ISSUE',
        url: 'https://github.com/owner/repo/issues/20',
        title: 'Fix Lucene BM25 index path traversal',
        cited: false,
        snippet: '',
      };
      expect(formatSourceTitle(source)).toBe('Issue #20 — Fix Lucene BM25 index path traversal');
    });

    it('returns pre-formatted titles untouched', () => {
      expect(formatSourceTitle({ title: 'commit abc1234 — foo' })).toBe('commit abc1234 — foo');
      expect(formatSourceTitle({ title: 'PR #16 — bar' })).toBe('PR #16 — bar');
      expect(formatSourceTitle({ title: 'Issue #20 — baz' })).toBe('Issue #20 — baz');
    });

    it('handles fallback when fields are missing', () => {
      expect(formatSourceTitle({ chunkType: 'COMMIT', url: 'https://github.com/owner/repo/commit/abc1234567' })).toBe('commit abc1234');
      expect(formatSourceTitle({ chunkType: 'PULL_REQUEST', url: 'https://github.com/owner/repo/pull/42' })).toBe('PR #42');
      expect(formatSourceTitle({ chunkType: 'ISSUE', url: 'https://github.com/owner/repo/issues/99' })).toBe('Issue #99');
      expect(formatSourceTitle({ path: null })).toBe('Unknown source');
    });
  });

  describe('formatElapsedTime', () => {
    it('formats seconds under one minute', () => {
      const start = '2026-10-27T10:00:00Z';
      const finish = '2026-10-27T10:00:42Z';
      expect(formatElapsedTime(start, finish)).toBe('42s');
    });

    it('formats minutes and seconds over one minute', () => {
      const start = '2026-10-27T10:00:00Z';
      const finish = '2026-10-27T10:02:05Z';
      expect(formatElapsedTime(start, finish)).toBe('2m 05s');
    });

    it('uses nowMs when finishedAt is null or active', () => {
      const start = new Date(10000).toISOString();
      const now = 25000;
      expect(formatElapsedTime(start, null, now)).toBe('15s');
    });

    it('returns 0s for missing or invalid dates', () => {
      expect(formatElapsedTime(null)).toBe('0s');
      expect(formatElapsedTime('invalid-date')).toBe('0s');
    });
  });

  describe('formatRelativeTime', () => {
    it('returns Never for null or undefined input', () => {
      expect(formatRelativeTime(null)).toBe('Never');
      expect(formatRelativeTime(undefined)).toBe('Never');
    });

    it('formats seconds and minutes correctly', () => {
      const now = Date.now();
      expect(formatRelativeTime(new Date(now - 5000).toISOString())).toBe('just now');
      expect(formatRelativeTime(new Date(now - 30000).toISOString())).toBe('30s ago');
      expect(formatRelativeTime(new Date(now - 120000).toISOString())).toBe('2m ago');
      expect(formatRelativeTime(new Date(now - 7200000).toISOString())).toBe('2h ago');
    });
  });
});
