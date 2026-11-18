import type { Meta, StoryObj } from '@storybook/react';

import { MacroHints } from './macro-hints';

const meta: Meta<typeof MacroHints> = {
  title: 'Components/MacroHints',
  component: MacroHints,
};

export default meta;
type Story = StoryObj<typeof MacroHints>;

export const GrabberMacros: Story = {
  args: {
    label: 'Макросы',
    macros: [
      '[name_of_the_source]',
      '[id_of_the_source]',
      '[post_id_of_the_source]',
      '[rand]a|b[/rand]',
      '[keyText]',
    ],
  },
};
