import { describe, expect, it } from 'vitest';

import { FileDropzone } from './file-dropzone';

describe('FileDropzone', () => {
  it('exports a component', () => {
    expect(FileDropzone).toBeTypeOf('function');
  });
});
