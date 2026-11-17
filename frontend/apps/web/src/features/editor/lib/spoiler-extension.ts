import { Mark, mergeAttributes } from '@tiptap/core';

/** Telegram-compatible spoiler mark → `<tg-spoiler>`. */
export const Spoiler = Mark.create({
  name: 'spoiler',
  parseHTML() {
    return [
      { tag: 'tg-spoiler' },
      { tag: 'span.tg-spoiler' },
    ];
  },
  renderHTML({ HTMLAttributes }) {
    return ['tg-spoiler', mergeAttributes(HTMLAttributes), 0];
  },
  addCommands() {
    return {
      toggleSpoiler:
        () =>
        ({ commands }) =>
          commands.toggleMark(this.name),
    };
  },
});

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    spoiler: {
      toggleSpoiler: () => ReturnType;
    };
  }
}
