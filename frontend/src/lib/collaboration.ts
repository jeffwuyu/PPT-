import * as Y from 'yjs';
import type { Presentation } from '../types';
import { getAccessToken, getStoredUser } from './storage';

export type CollaborationMember = {
  actorUserId?: number;
  userId?: number;
  username?: string;
  clientId?: string;
  [key: string]: unknown;
};

export type RemoteCursor = {
  clientId: string;
  actorUserId: number;
  slideId: number;
  elementId?: number;
  x?: number;
  y?: number;
};

export type CollaborationClient = {
  clientId: string;
  doc: Y.Doc;
  close: () => void;
  publishPresentation: (presentation: Presentation) => void;
  sendCursor: (cursor: Omit<RemoteCursor, 'clientId' | 'actorUserId'>) => void;
  acquireLock: (targetType: string, targetId: number, operation: string) => string;
  releaseLock: (lockToken: string) => void;
};

type Handlers = {
  onPresentation?: (presentation: Presentation) => void;
  onPresence?: (members: CollaborationMember[]) => void;
  onCursor?: (cursor: RemoteCursor) => void;
  onLock?: (event: Record<string, unknown>) => void;
  onError?: (message: string) => void;
};

const WS_BASE = import.meta.env.VITE_WS_BASE || '/ws';

export function connectCollaboration(presentationId: number, initial: Presentation, handlers: Handlers): CollaborationClient {
  const doc = new Y.Doc();
  const state = doc.getMap<unknown>('presentation');
  const clientId = crypto.randomUUID();
  const token = getAccessToken();
  const actorUserId = getStoredUser()?.id || 0;
  let applyingRemote = false;
  let opened = false;
  const ws = new WebSocket(`${wsUrl()}/presentations/${presentationId}?access_token=${encodeURIComponent(token || '')}`);

  state.set('snapshot', initial);

  doc.on('update', (update) => {
    if (!opened || applyingRemote) {
      return;
    }
    ws.send(JSON.stringify({
      type: 'yjs-update',
      clientId,
      update: bytesToBase64(update)
    }));
  });

  ws.addEventListener('open', () => {
    opened = true;
    ws.send(JSON.stringify({ type: 'join', clientId, lastUpdateId: 0 }));
  });

  ws.addEventListener('message', (event) => {
    const message = JSON.parse(event.data);
    if (message.type === 'sync-state') {
      applyingRemote = true;
      for (const update of message.updates || []) {
        Y.applyUpdate(doc, base64ToBytes(update.update));
      }
      applyingRemote = false;
      const current = state.get('snapshot') as Presentation | undefined;
      if (current) {
        handlers.onPresentation?.(current);
      }
      return;
    }
    if (message.type === 'yjs-update') {
      applyingRemote = true;
      Y.applyUpdate(doc, base64ToBytes(message.update));
      applyingRemote = false;
      const current = state.get('snapshot') as Presentation | undefined;
      if (current) {
        handlers.onPresentation?.(current);
      }
      return;
    }
    if (message.type === 'presence') {
      handlers.onPresence?.(message.members || []);
      return;
    }
    if (message.type === 'cursor') {
      if (message.clientId !== clientId) {
        handlers.onCursor?.(message as RemoteCursor);
      }
      return;
    }
    if (message.type?.startsWith('lock-')) {
      handlers.onLock?.(message);
      return;
    }
    if (message.type === 'error') {
      handlers.onError?.(message.message || 'Collaboration error');
    }
  });

  ws.addEventListener('error', () => handlers.onError?.('Collaboration connection failed'));

  return {
    clientId,
    doc,
    close: () => ws.close(),
    publishPresentation: (presentation) => {
      state.set('snapshot', presentation);
    },
    sendCursor: (cursor) => {
      if (ws.readyState !== WebSocket.OPEN) {
        return;
      }
      ws.send(JSON.stringify({
        type: 'cursor',
        clientId,
        slideId: cursor.slideId,
        elementId: cursor.elementId,
        x: cursor.x,
        y: cursor.y
      }));
    },
    acquireLock: (targetType, targetId, operation) => {
      const lockToken = crypto.randomUUID();
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'lock-acquire', clientId, targetType, targetId, operation, lockToken }));
      }
      return lockToken;
    },
    releaseLock: (lockToken) => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'lock-release', clientId, lockToken }));
      }
    }
  };
}

function wsUrl() {
  if (WS_BASE.startsWith('ws')) {
    return WS_BASE.replace(/\/$/, '');
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}${WS_BASE}`;
}

function bytesToBase64(bytes: Uint8Array) {
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

function base64ToBytes(value: string) {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}
