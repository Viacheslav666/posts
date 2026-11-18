'use client';

import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';

import type { ToastActionElement, ToastProps } from '../components/toast';

const TOAST_LIMIT = 3;
const TOAST_REMOVE_DELAY = 400;

/** Default auto-dismiss for success / default toasts (ms). */
export const DEFAULT_TOAST_DURATION = 4000;
/** Longer linger for errors so users can read them. */
export const DESTRUCTIVE_TOAST_DURATION = 8000;

type ToasterToast = ToastProps & {
  id: string;
  title?: ReactNode;
  description?: ReactNode;
  action?: ToastActionElement;
};

type ActionType = {
  ADD_TOAST: 'ADD_TOAST';
  UPDATE_TOAST: 'UPDATE_TOAST';
  DISMISS_TOAST: 'DISMISS_TOAST';
  REMOVE_TOAST: 'REMOVE_TOAST';
};

type Action =
  | { type: ActionType['ADD_TOAST']; toast: ToasterToast }
  | { type: ActionType['UPDATE_TOAST']; toast: Partial<ToasterToast> }
  | { type: ActionType['DISMISS_TOAST']; toastId?: string }
  | { type: ActionType['REMOVE_TOAST']; toastId?: string };

interface State {
  toasts: ToasterToast[];
}

let count = 0;

function genId() {
  count = (count + 1) % Number.MAX_SAFE_INTEGER;
  return count.toString();
}

const toastTimeouts = new Map<string, ReturnType<typeof setTimeout>>();
const dismissTimeouts = new Map<string, ReturnType<typeof setTimeout>>();

function addToRemoveQueue(toastId: string) {
  if (toastTimeouts.has(toastId)) {
    return;
  }

  const timeout = setTimeout(() => {
    toastTimeouts.delete(toastId);
    dispatch({ type: 'REMOVE_TOAST', toastId });
  }, TOAST_REMOVE_DELAY);

  toastTimeouts.set(toastId, timeout);
}

function clearDismissTimeout(toastId: string) {
  const existing = dismissTimeouts.get(toastId);
  if (existing) {
    clearTimeout(existing);
    dismissTimeouts.delete(toastId);
  }
}

export function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'ADD_TOAST':
      return { ...state, toasts: [action.toast, ...state.toasts].slice(0, TOAST_LIMIT) };
    case 'UPDATE_TOAST':
      return {
        ...state,
        toasts: state.toasts.map((toast) => (toast.id === action.toast.id ? { ...toast, ...action.toast } : toast)),
      };
    case 'DISMISS_TOAST': {
      const { toastId } = action;
      if (toastId) {
        clearDismissTimeout(toastId);
        addToRemoveQueue(toastId);
      } else {
        state.toasts.forEach((toast) => {
          clearDismissTimeout(toast.id);
          addToRemoveQueue(toast.id);
        });
      }
      return {
        ...state,
        toasts: state.toasts.map((toast) =>
          toast.id === toastId || toastId === undefined ? { ...toast, open: false } : toast,
        ),
      };
    }
    case 'REMOVE_TOAST':
      if (action.toastId === undefined) {
        return { ...state, toasts: [] };
      }
      return { ...state, toasts: state.toasts.filter((toast) => toast.id !== action.toastId) };
    default:
      return state;
  }
}

const listeners: Array<(state: State) => void> = [];
let memoryState: State = { toasts: [] };

function dispatch(action: Action) {
  memoryState = reducer(memoryState, action);
  listeners.forEach((listener) => listener(memoryState));
}

type ToastInput = Omit<ToasterToast, 'id'>;

function resolveDuration(props: ToastInput): number {
  if (typeof props.duration === 'number') {
    return props.duration;
  }
  return props.variant === 'destructive' ? DESTRUCTIVE_TOAST_DURATION : DEFAULT_TOAST_DURATION;
}

function toast({ ...props }: ToastInput) {
  const id = genId();
  const duration = resolveDuration(props);

  const update = (next: ToasterToast) => dispatch({ type: 'UPDATE_TOAST', toast: { ...next, id } });
  const dismiss = () => dispatch({ type: 'DISMISS_TOAST', toastId: id });

  dispatch({
    type: 'ADD_TOAST',
    toast: {
      ...props,
      id,
      duration,
      open: true,
      onOpenChange: (open) => {
        if (!open) dismiss();
      },
    },
  });

  // Explicit auto-dismiss so success toasts close even after route changes
  // (controlled `open` + module memory can otherwise leave them stuck).
  if (duration !== Infinity) {
    clearDismissTimeout(id);
    const timeout = setTimeout(() => dismiss(), duration);
    dismissTimeouts.set(id, timeout);
  }

  return { id, dismiss, update };
}

export function useToast() {
  const [state, setState] = useState<State>(memoryState);

  useEffect(() => {
    listeners.push(setState);
    return () => {
      const index = listeners.indexOf(setState);
      if (index > -1) {
        listeners.splice(index, 1);
      }
    };
  }, []);

  return {
    ...state,
    toast,
    dismiss: (toastId?: string) => dispatch({ type: 'DISMISS_TOAST', toastId }),
  };
}

export { toast };
