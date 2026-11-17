import type { Meta, StoryObj } from '@storybook/react';
import { useState } from 'react';

import { FileDropzone } from './file-dropzone';

const meta: Meta<typeof FileDropzone> = {
  title: 'Forms/FileDropzone',
  component: FileDropzone,
  parameters: { a11y: { test: 'todo' } },
};

export default meta;
type Story = StoryObj<typeof FileDropzone>;

export const Default: Story = {
  render: () => {
    const [names, setNames] = useState<string[]>([]);
    return (
      <div className="max-w-md space-y-3 p-4">
        <FileDropzone
          label="Перетащите файлы или выберите"
          hint="JPG, PNG, WEBP, GIF, MP4, PDF"
          onFiles={(files) => setNames(files.map((file) => file.name))}
        />
        {names.length > 0 ? (
          <ul className="text-sm text-muted-foreground">
            {names.map((name) => (
              <li key={name}>{name}</li>
            ))}
          </ul>
        ) : null}
      </div>
    );
  },
};

export const Disabled: Story = {
  args: {
    label: 'Загрузка недоступна',
    disabled: true,
    onFiles: () => undefined,
  },
};
