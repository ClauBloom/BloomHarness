import type { Component } from 'vue';

export interface MenuItem {
  id: string;
  label: string;
  icon?: Component;
  danger?: boolean;
  disabled?: boolean;
  /** Secondary text shown right-aligned (e.g. a shortcut or path). */
  hint?: string;
}

export type MenuEntry = MenuItem | { type: 'separator' } | { type: 'label'; text: string };

export function isMenuItem(entry: MenuEntry): entry is MenuItem {
  return !('type' in entry);
}
