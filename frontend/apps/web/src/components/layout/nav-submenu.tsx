'use client';

import { cn } from '@posts/ui';
import Link from 'next/link';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

import { placeMenu, type NavNode } from './nav-model';

type NavSubmenuProps = {
  items: NavNode[];
  label: string;
  menuId: string;
  anchor: HTMLElement;
  pathname: string;
  onClose: () => void;
};

export function NavSubmenu({ items, label, menuId, anchor, pathname, onClose }: NavSubmenuProps) {
  const [placement, setPlacement] = useState(() =>
    placeMenu(anchor.getBoundingClientRect(), { width: window.innerWidth, height: window.innerHeight }, items.length),
  );
  const [index, setIndex] = useState(0);
  const links = useRef<Array<HTMLAnchorElement | null>>([]);

  useLayoutEffect(() => {
    const update = () => {
      const rect = anchor.getBoundingClientRect();
      if (rect.bottom < 0 || rect.top > window.innerHeight) {
        onClose();
        return;
      }
      setPlacement(placeMenu(rect, { width: window.innerWidth, height: window.innerHeight }, items.length));
    };
    update();
    window.addEventListener('resize', update);
    window.addEventListener('scroll', update, true);
    return () => {
      window.removeEventListener('resize', update);
      window.removeEventListener('scroll', update, true);
    };
  }, [anchor, items.length, onClose]);

  useEffect(() => {
    links.current[index]?.focus();
  }, [index]);

  if (typeof document === 'undefined') {
    return null;
  }

  return createPortal(
    <div
      id={menuId}
      role="menu"
      aria-label={label}
      data-nav-menu
      data-testid={`nav-menu-${label}`}
      className="fixed z-50 overflow-y-auto rounded-md border border-border bg-popover p-1 shadow-md"
      style={{ top: placement.top, left: placement.left, width: 240, maxHeight: placement.maxHeight }}
      onKeyDown={(event) => {
        if (event.key === 'ArrowDown') {
          event.preventDefault();
          setIndex((current) => (current + 1) % items.length);
        } else if (event.key === 'ArrowUp') {
          event.preventDefault();
          setIndex((current) => (current - 1 + items.length) % items.length);
        } else if (event.key === 'Escape') {
          event.preventDefault();
          onClose();
        } else if (event.key === 'Home') {
          event.preventDefault();
          setIndex(0);
        } else if (event.key === 'End') {
          event.preventDefault();
          setIndex(items.length - 1);
        }
      }}
    >
      {items.map((item, itemIndex) => {
        const pathOnly = item.href.split(/[?#]/)[0] ?? item.href;
        const current = pathname === pathOnly;
        return (
          <Link
            key={item.id}
            ref={(node) => {
              links.current[itemIndex] = node;
            }}
            href={item.href}
            role="menuitem"
            tabIndex={itemIndex === index ? 0 : -1}
            data-testid={`nav-child-${item.id}`}
            className={cn(
              'block rounded-sm px-2 py-1.5 text-sm text-popover-foreground hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              current && 'bg-primary/10 text-primary',
            )}
            onClick={onClose}
          >
            {item.label}
          </Link>
        );
      })}
    </div>,
    document.body,
  );
}
