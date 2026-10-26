import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import { CHAT_ACCEPTED_FILES, chatPath, errorMessage, fetchAttachmentUrl, formatChatTime, formatFileSize, isSameSubject, mergeMessages, sendChatMessage, validateChatFile } from "../api/chatApi";
import { useChat } from "../context/ChatContext";

function ChatImage({ message }) {
  const [url, setUrl] = useState(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let created = null;
    let cancelled = false;
    fetchAttachmentUrl(message.id).then((objectUrl) => { created = objectUrl; if (cancelled) URL.revokeObjectURL(objectUrl); else setUrl(objectUrl); }).catch(() => setFailed(true));
    return () => { cancelled = true; if (created) URL.revokeObjectURL(created); };
  }, [message.id]);
  if (failed) return <span className="chat-file-error">Image could not be loaded</span>;
  if (!url) return <span className="chat-image-placeholder">Loading image…</span>;
  return <a href={url} target="_blank" rel="noreferrer" className="chat-image-link"><img src={url} alt={message.attachment.name} className="chat-image" /></a>;
}

function ChatFile({ message }) {
  const [error, setError] = useState("");
  async function download() {
    setError("");
    try {
      const url = await fetchAttachmentUrl(message.id);
      const link = document.createElement("a");
      link.href = url; link.download = message.attachment.name;
      document.body.appendChild(link); link.click(); link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (downloadError) { setError(downloadError.message); }
  }
  return <button type="button" className="chat-file" onClick={download} title="Download">
    <span className="chat-file-icon" aria-hidden="true">📎</span>
    <span className="chat-file-copy"><strong>{message.attachment.name}</strong><small>{error || formatFileSize(message.attachment.size)}</small></span>
  </button>;
}

function AddPeople({ subjectType, subjectId, participants, onAdded }) {
  const [open, setOpen] = useState(false);
  const [people, setPeople] = useState([]);
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    if (!open || people.length) return;
    apiFetch("/api/chat/people").then((response) => response.ok ? response.json() : []).then(setPeople).catch(() => setPeople([]));
  }, [open, people.length]);
  const existing = useMemo(() => new Set(participants.map((person) => person.id)), [participants]);
  const matches = people.filter((person) => !existing.has(person.id)
    && `${person.name} ${person.departmentName || ""}`.toLowerCase().includes(query.trim().toLowerCase())).slice(0, 8);

  async function add(person) {
    setError("");
    const response = await apiFetch(`${chatPath(subjectType, subjectId)}/participants`, { method: "POST", body: JSON.stringify({ userId: person.id }) });
    if (!response.ok) { setError(await errorMessage(response, "Could not add this person.")); return; }
    onAdded(await response.json());
    setQuery("");
  }

  if (!open) return <button type="button" className="chat-link-button" onClick={() => setOpen(true)}>+ Add people</button>;
  return <div className="chat-add-people">
    <div className="chat-add-people-row">
      <input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search name or department" aria-label="Search people to add" />
      <button type="button" className="chat-link-button" onClick={() => setOpen(false)}>Done</button>
    </div>
    {error && <p className="chat-error">{error}</p>}
    <ul>{matches.map((person) => <li key={person.id}><button type="button" onClick={() => add(person)}><strong>{person.name}</strong><span>{person.departmentName || "No department"}</span></button></li>)}</ul>
    {people.length > 0 && !matches.length && <p className="chat-muted">No one else matches.</p>}
  </div>;
}

/** Chat for one interdepartment request (subjectType "request"). */
export default function ChatPanel({ subjectType, subjectId, user, showTitle = false }) {
  const { subscribe, refreshUnread } = useChat();
  const [view, setView] = useState(null);
  const [messages, setMessages] = useState([]);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState("");
  const [text, setText] = useState("");
  const [file, setFile] = useState(null);
  const [sending, setSending] = useState(false);
  const listRef = useRef(null);
  const fileRef = useRef(null);
  const stickToBottom = useRef(true);
  const lastIdRef = useRef(0);
  const participantIds = useRef(new Set());
  const isAdmin = user?.role === "ADMIN";

  const markRead = useCallback(async () => {
    await apiFetch(`${chatPath(subjectType, subjectId)}/read`, { method: "POST" }).catch(() => null);
    void refreshUnread();
  }, [subjectType, subjectId, refreshUnread]);

  const load = useCallback(async () => {
    setError("");
    try {
      const response = await apiFetch(chatPath(subjectType, subjectId));
      if (!response.ok) throw new Error(response.status === 404 ? "This chat is not available to you." : `Chat could not be loaded (${response.status}).`);
      const data = await response.json();
      setView(data); setMessages(data.messages); setHasMore(data.hasMore);
      stickToBottom.current = true;
      void markRead();
    } catch (loadError) { setError(loadError.message); }
  }, [subjectType, subjectId, markRead]);

  const catchUp = useCallback(async () => {
    const response = await apiFetch(`${chatPath(subjectType, subjectId)}/messages?after=${lastIdRef.current}`).catch(() => null);
    if (response?.ok) { const found = await response.json(); if (found.length) setMessages((current) => mergeMessages(current, found)); }
  }, [subjectType, subjectId]);

  // Someone who writes for the first time joins the chat; refresh the "In this chat" line.
  const refreshParticipants = useCallback(async () => {
    const response = await apiFetch(chatPath(subjectType, subjectId)).catch(() => null);
    if (response?.ok) { const data = await response.json(); setView((current) => current ? { ...current, participants: data.participants } : data); }
  }, [subjectType, subjectId]);

  useEffect(() => { participantIds.current = new Set((view?.participants || []).map((person) => person.id)); }, [view]);

  useEffect(() => { setView(null); setMessages([]); void load(); }, [load]);

  useEffect(() => subscribe((event) => {
    if (event.kind === "RECONNECTED") { void catchUp(); return; }
    if (!isSameSubject(event, subjectType, subjectId)) return;
    if (event.kind === "PARTICIPANTS") { void refreshParticipants(); return; }
    if (event.message && !participantIds.current.has(event.message.senderId)) void refreshParticipants();
    if (event.message) {
      setMessages((current) => mergeMessages(current, [event.message]));
      if (event.kind === "MESSAGE" && document.visibilityState === "visible") void markRead();
    }
  }), [subscribe, subjectType, subjectId, catchUp, refreshParticipants, markRead]);

  useEffect(() => {
    lastIdRef.current = messages.length ? messages[messages.length - 1].id : 0;
    const list = listRef.current;
    if (list && stickToBottom.current) list.scrollTop = list.scrollHeight;
  }, [messages]);

  async function loadOlder() {
    const list = listRef.current;
    const previousHeight = list?.scrollHeight || 0;
    const response = await apiFetch(`${chatPath(subjectType, subjectId)}?before=${messages[0]?.id}`);
    if (!response.ok) return;
    const data = await response.json();
    stickToBottom.current = false;
    setMessages((current) => mergeMessages(current, data.messages)); setHasMore(data.hasMore);
    requestAnimationFrame(() => { if (list) list.scrollTop = list.scrollHeight - previousHeight; });
  }

  function chooseFile(event) {
    const chosen = event.target.files?.[0] || null;
    event.target.value = "";
    const problem = validateChatFile(chosen);
    if (problem) { setError(problem); return; }
    setError(""); setFile(chosen);
  }

  async function send(event) {
    event?.preventDefault();
    if (sending || (!text.trim() && !file)) return;
    setSending(true); setError("");
    try {
      const sent = await sendChatMessage(subjectType, subjectId, text, file);
      stickToBottom.current = true;
      setMessages((current) => mergeMessages(current, [sent]));
      setText(""); setFile(null);
      if (!participantIds.current.has(user?.userId)) void refreshParticipants();
    } catch (sendError) { setError(sendError.message); }
    finally { setSending(false); }
  }

  async function remove(message) {
    if (!window.confirm("Delete this message?")) return;
    const response = await apiFetch(`/api/chat/messages/${message.id}`, { method: "DELETE" });
    if (response.ok) setMessages((current) => mergeMessages(current, [{ ...message, deleted: true, body: null, attachment: null }]));
    else setError("Message could not be deleted.");
  }

  const participants = view?.participants || [];
  return <section className="chat-panel" aria-label="Chat">
    {showTitle && view && <header className="chat-panel-title"><h2>{view.title}</h2><span>Request{view.departmentName ? ` · ${view.departmentName}` : ""}</span></header>}
    <div className="chat-people">
      <span className="chat-muted">{participants.length ? `In this chat: ${participants.map((person) => person.name).join(", ")}` : "No messages yet. Start the conversation."}</span>
      {view && <AddPeople subjectType={subjectType} subjectId={subjectId} participants={participants} onAdded={(people) => setView((current) => ({ ...current, participants: people }))} />}
    </div>
    <div className="chat-messages" ref={listRef} onScroll={(event) => { const list = event.currentTarget; stickToBottom.current = list.scrollHeight - list.scrollTop - list.clientHeight < 60; }}>
      {hasMore && <button type="button" className="chat-link-button chat-older" onClick={loadOlder}>Show older messages</button>}
      {!view && !error && <p className="chat-muted">Loading chat…</p>}
      {messages.map((message) => {
        const mine = message.senderId === user?.userId;
        return <div key={message.id} className={`chat-message ${mine ? "is-mine" : ""} ${message.deleted ? "is-deleted" : ""}`}>
          <div className="chat-message-meta"><strong>{mine ? "You" : message.senderName}</strong>{!mine && message.senderDepartment && <span>{message.senderDepartment}</span>}<time dateTime={message.createdAt}>{formatChatTime(message.createdAt)}</time></div>
          {message.deleted ? <p className="chat-message-body">Message deleted</p> : <>
            {message.body && <p className="chat-message-body">{message.body}</p>}
            {message.attachment && (message.attachment.image ? <ChatImage message={message} /> : <ChatFile message={message} />)}
          </>}
          {!message.deleted && (mine || isAdmin) && <button type="button" className="chat-delete" onClick={() => remove(message)} aria-label="Delete message">Delete</button>}
        </div>;
      })}
    </div>
    {error && <p className="chat-error" role="alert">{error}</p>}
    {view && <form className="chat-composer" onSubmit={send}>
      {file && <div className="chat-pending-file"><span>📎 {file.name} ({formatFileSize(file.size)})</span><button type="button" onClick={() => setFile(null)} aria-label="Remove file">×</button></div>}
      <div className="chat-composer-row">
        <input ref={fileRef} type="file" accept={CHAT_ACCEPTED_FILES} onChange={chooseFile} hidden />
        <button type="button" className="chat-attach" onClick={() => fileRef.current?.click()} aria-label="Attach file" title="Attach image or file (max 10 MB)">📎</button>
        <textarea value={text} onChange={(event) => setText(event.target.value)} rows={1} maxLength={4000} placeholder="Write a message…" aria-label="Message"
          onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void send(); } }} />
        <button type="submit" className="primary-button chat-send" disabled={sending || (!text.trim() && !file)}>{sending ? "Sending…" : "Send"}</button>
      </div>
    </form>}
  </section>;
}
