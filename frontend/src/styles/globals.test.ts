import { describe, it, expect } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

describe('globals.css cascade layer order', () => {
  it('starts with the @layer declaration to establish precedence over legacy css', () => {
    const globalsPath = path.resolve(__dirname, 'globals.css');
    const content = fs.readFileSync(globalsPath, 'utf8').trimStart();
    const firstLine = content.split('\n')[0].trim();

    expect(firstLine).toBe('@layer theme, base, components, utilities;');
  });
});
