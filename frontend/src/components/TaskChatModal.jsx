import ChatPanel from "./ChatPanel";

/** Chat for a task opened from a card that has no details dialog (My Kanban). */
export default function TaskChatModal({ task, user, onClose }) {
  if (!task) return null;
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <div className="modal-card modal-card-chat" role="dialog" aria-modal="true" aria-labelledby="task-chat-title">
      <div className="modal-header">
        <div><p className="eyebrow">Task chat</p><h2 id="task-chat-title">{task.title}</h2></div>
        <button type="button" className="modal-close-button" onClick={onClose} aria-label="Close dialog">&times;</button>
      </div>
      <ChatPanel subjectType="task" subjectId={task.id} user={user} />
    </div>
  </div>;
}
