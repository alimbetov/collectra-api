type SessionLostListener = () => void;

const listeners = new Set<SessionLostListener>();

export function subscribeSessionLost(listener: SessionLostListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function emitSessionLost(): void {
  listeners.forEach((listener) => listener());
}
