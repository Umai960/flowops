import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import { formatChatTime } from "../api/chatApi";
import ChatPanel from "../components/ChatPanel";
import { useChat } from "../context/ChatContext";

// Every chat the user follows, plus (for managers and admins) every chat they are allowed to read.
export default function ChatPage({ user, target, onSelect }) {
  const { subscribe } = useChat();
  const canSeeAll = user?.role === "ADMIN" || user?.role === "MANAGER";
  const [scope, setScope] = useState("mine");
  const [threads, setThreads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const response = await apiFetch(`/api/chat/threads?scope=${scope}`);
      if (!response.ok) throw new Error(`Chats could not be loaded (${response.status}).`);
      setThreads(await response.json()); setError("");
    } catch (loadError) { setError(loadError.message); } finally { setLoading(false); }
  }, [scope]);

  useEffect(() => { setLoading(true); void load(); }, [load]);
  useEffect(() => {
    let timer = null;
    // At most one list refresh per short burst of messages.
    const unsubscribe = subscribe(() => { if (!timer) timer = window.setTimeout(() => { timer = null; void load(); }, 400); });
    return () => { unsubscribe(); window.clearTimeout(timer); };
  }, [subscribe, load]);

  const isSelected = (thread) => target && thread.subjectType.toLowerCase() === target.type && thread.subjectId === target.id;
  return <section className="chat-page">
    <div className="admin-page-heading"><div><p className="eyebrow">Workspace</p><h1>Chat</h1><p>Conversations inside tasks and planning items. Messages are kept for 3 months.</p></div></div>
    <div className="chat-page-layout">
      <aside className="chat-thread-list" aria-label="Chats">
        {canSeeAll && <div className="chat-scope" role="tablist">
          <button type="button" role="tab" aria-selected={scope === "mine"} className={scope === "mine" ? "is-active" : ""} onClick={() => setScope("mine")}>My chats</button>
          <button type="button" role="tab" aria-selected={scope === "all"} className={scope === "all" ? "is-active" : ""} onClick={() => setScope("all")}>{user?.role === "ADMIN" ? "All chats" : "Department chats"}</button>
        </div>}
        {error && <p className="chat-error">{error}</p>}
        {loading && !threads.length && <p className="chat-muted">Loading…</p>}
        {!loading && !threads.length && !error && <p className="chat-muted">No chats yet. Open a task or planning item and use its Chat tab.</p>}
        {threads.map((thread) => <button key={`${thread.subjectType}-${thread.subjectId}`} type="button" className={`chat-thread ${isSelected(thread) ? "is-active" : ""}`}
          onClick={() => onSelect({ type: thread.subjectType.toLowerCase(), id: thread.subjectId })}>
          <span className="chat-thread-top"><strong>{thread.title}</strong>{thread.unreadCount > 0 && <span className="chat-unread">{thread.unreadCount}</span>}</span>
          <span className="chat-thread-meta">{thread.subjectType === "TASK" ? "Task" : "Planning"}{thread.departmentName ? ` · ${thread.departmentName}` : ""} · {formatChatTime(thread.lastMessageAt)}</span>
          {thread.lastMessagePreview && <span className="chat-thread-preview">{thread.lastMessagePreview}</span>}
        </button>)}
      </aside>
      <div className="chat-page-panel">
        {target ? <ChatPanel key={`${target.type}-${target.id}`} subjectType={target.type} subjectId={target.id} user={user} showTitle />
          : <p className="chat-muted chat-page-empty">Choose a chat on the left.</p>}
      </div>
    </div>
  </section>;
}
