import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import { authorizedFetch, createSseParser } from "../api/chatApi";
import { useAuth } from "./AuthContext";
import { useNotifications } from "./NotificationContext";

const ChatContext = createContext(null);
const MAX_RETRY_MS = 30000;

// One live connection per tab. Chat panels subscribe to it so new messages appear immediately.
export function ChatProvider({ children }) {
  const { isAuthenticated, user } = useAuth();
  const { fetchUnreadCount: refreshBell } = useNotifications();
  const authKey = isAuthenticated ? user?.userId : null;
  const listeners = useRef(new Set());
  const [unreadCount, setUnreadCount] = useState(0);
  const [connected, setConnected] = useState(false);

  const refreshUnread = useCallback(async () => {
    if (authKey == null) return;
    try {
      const response = await apiFetch("/api/chat/unread-count");
      if (response.ok) setUnreadCount(Number((await response.json()).count) || 0);
    } catch (error) {
      console.error("Failed to load chat unread count:", error);
    }
  }, [authKey]);

  const subscribe = useCallback((listener) => {
    listeners.current.add(listener);
    return () => listeners.current.delete(listener);
  }, []);

  useEffect(() => {
    setUnreadCount(0);
    if (authKey == null) return undefined;
    let stopped = false;
    let controller = null;
    let retryMs = 1000;
    const dispatch = (event) => listeners.current.forEach((listener) => {
      try { listener(event); } catch (error) { console.error("Chat listener failed:", error); }
    });

    async function run() {
      while (!stopped) {
        controller = new AbortController();
        try {
          const response = await authorizedFetch("/api/chat/stream", { headers: { Accept: "text/event-stream" }, cache: "no-store", signal: controller.signal });
          if (response.status === 401 || response.status === 403) return;
          if (!response.ok || !response.body) throw new Error(`Chat stream failed (${response.status}).`);
          retryMs = 1000;
          setConnected(true);
          // Anything sent while we were disconnected is fetched by the open panels.
          dispatch({ kind: "RECONNECTED" });
          void refreshUnread();
          const parse = createSseParser((name, data) => {
            if (name !== "chat") return;
            const event = JSON.parse(data);
            dispatch(event);
            if (event.kind === "MESSAGE" || event.kind === "PARTICIPANTS") { void refreshUnread(); void refreshBell(); }
          });
          const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
          for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            parse(value);
          }
        } catch (error) {
          if (stopped || error.name === "AbortError") return;
        }
        setConnected(false);
        await new Promise((resolve) => window.setTimeout(resolve, retryMs));
        retryMs = Math.min(retryMs * 2, MAX_RETRY_MS);
      }
    }

    void refreshUnread();
    void run();
    return () => { stopped = true; controller?.abort(); setConnected(false); };
  }, [authKey, refreshUnread, refreshBell]);

  const value = useMemo(() => ({ subscribe, unreadCount, refreshUnread, connected }), [subscribe, unreadCount, refreshUnread, connected]);
  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>;
}

export function useChat() { return useContext(ChatContext); }
