import type { SourceResponse } from '../api/client';
import { safeHttpUrl } from './safeUrl';

export interface RemarkCitationsOptions {
  sources: SourceResponse[];
}

interface AstNode {
  type: string;
  value?: string;
  children?: AstNode[];
  url?: string;
  data?: {
    hProperties?: Record<string, unknown>;
    [key: string]: unknown;
  };
  [key: string]: unknown;
}

/**
 * Remark plugin that converts verified citation markers [n] into external link nodes
 * targeting the cited source's safe HTTP/HTTPS URL (opening in a new tab with rel="noopener noreferrer").
 * Citation numbers not present in sources or lacking a safe HTTP/HTTPS URL remain literal plain text.
 * Code blocks, inline code snippets, and existing links are preserved without modification.
 */
export function remarkCitations(options: RemarkCitationsOptions) {
  const sourceMap = new Map<number, string>();
  if (Array.isArray(options?.sources)) {
    for (const s of options.sources) {
      if (s && typeof s.number === 'number') {
        const safe = safeHttpUrl(s.url);
        if (safe) {
          sourceMap.set(s.number, safe);
        }
      }
    }
  }

  return (tree: AstNode) => {
    function visit(node: AstNode) {
      // Preserve code blocks, inline code, and existing links untouched
      if (node.type === 'code' || node.type === 'inlineCode' || node.type === 'link') {
        return;
      }

      if (Array.isArray(node.children)) {
        const newChildren: AstNode[] = [];

        for (const child of node.children) {
          if (child.type === 'text' && typeof child.value === 'string') {
            const textValue = child.value;
            const regex = /\[(\d+)\]/g;
            let lastIndex = 0;
            let match: RegExpExecArray | null;
            let transformed = false;

            while ((match = regex.exec(textValue)) !== null) {
              const num = parseInt(match[1], 10);
              const url = sourceMap.get(num);

              if (url) {
                transformed = true;
                const before = textValue.slice(lastIndex, match.index);
                if (before) {
                  newChildren.push({ type: 'text', value: before });
                }

                newChildren.push({
                  type: 'link',
                  url,
                  data: {
                    hProperties: {
                      className: 'citation-badge-link',
                      target: '_blank',
                      rel: 'noopener noreferrer',
                      'data-citation-number': num,
                      title: `Open source [${num}] on GitHub`,
                    },
                  },
                  children: [{ type: 'text', value: `[${num}]` }],
                });

                lastIndex = regex.lastIndex;
              }
            }

            if (transformed) {
              const after = textValue.slice(lastIndex);
              if (after) {
                newChildren.push({ type: 'text', value: after });
              }
            } else {
              newChildren.push(child);
            }
          } else {
            visit(child);
            newChildren.push(child);
          }
        }

        node.children = newChildren;
      }
    }

    visit(tree);
  };
}
