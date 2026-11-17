'use client';

import { Upload } from 'lucide-react';
import {
  type ChangeEvent,
  type DragEvent,
  type HTMLAttributes,
  useId,
  useRef,
  useState,
} from 'react';

import { cn } from '../lib/utils';

export type FileDropzoneProps = Omit<HTMLAttributes<HTMLDivElement>, 'onDrop' | 'onChange'> & {
  accept?: string;
  multiple?: boolean;
  disabled?: boolean;
  label: string;
  hint?: string;
  onFiles: (files: File[]) => void;
};

export function FileDropzone({
  accept,
  multiple = true,
  disabled = false,
  label,
  hint,
  onFiles,
  className,
  ...props
}: FileDropzoneProps) {
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  const emit = (list: FileList | File[] | null) => {
    if (!list || disabled) return;
    const files = Array.from(list);
    if (files.length) onFiles(files);
  };

  const onDragOver = (event: DragEvent) => {
    event.preventDefault();
    if (!disabled) setDragging(true);
  };

  const onDragLeave = (event: DragEvent) => {
    event.preventDefault();
    setDragging(false);
  };

  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    setDragging(false);
    emit(event.dataTransfer.files);
  };

  const onChange = (event: ChangeEvent<HTMLInputElement>) => {
    emit(event.target.files);
    event.target.value = '';
  };

  return (
    <div
      data-testid="file-dropzone"
      className={cn(
        'relative rounded-lg border border-dashed border-border bg-muted/20 px-4 py-8 text-center transition-colors',
        dragging && 'border-primary bg-primary/5',
        disabled && 'opacity-50',
        className,
      )}
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
      {...props}
    >
      <input
        ref={inputRef}
        id={inputId}
        type="file"
        className="absolute inset-0 z-10 h-full w-full cursor-pointer opacity-0 disabled:cursor-not-allowed"
        accept={accept}
        multiple={multiple}
        disabled={disabled}
        onChange={onChange}
        aria-label={label}
      />
      <div className="pointer-events-none flex flex-col items-center justify-center gap-2">
        <Upload className="h-6 w-6 text-muted-foreground" aria-hidden />
        <p className="text-sm font-medium text-foreground">{label}</p>
        {hint ? <p className="max-w-sm text-xs text-muted-foreground">{hint}</p> : null}
      </div>
    </div>
  );
}
