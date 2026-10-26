import { useEffect, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import RddTaskActions from "./RddTaskActions";

const API_BASE_URL = "";

function EditTaskModal({ task, users, user, onClose, onTaskUpdated, onDelete, canDelete }) {
  const [title, setTitle] = useState(task?.title || "");
  const [description, setDescription] = useState(task?.description || "");
  const [priority, setPriority] = useState(task?.priority || "MEDIUM");
  const [dueDate, setDueDate] = useState(task?.dueDate || "");
  const [assigneeId, setAssigneeId] = useState(
    task?.assigneeId != null ? String(task.assigneeId) : ""
  );
  const [workload, setWorkload] = useState(String(task?.workload ?? 3));
  const [error, setError] = useState("");
  const [checkpoints, setCheckpoints] = useState([]);
  const [checkpointTitle, setCheckpointTitle] = useState("");
  const isRddTask = task?.departmentName?.toUpperCase() === "RDD";

  useEffect(() => {
    if (!task || !isRddTask) { setCheckpoints([]); return; }
    apiFetch(`${API_BASE_URL}/api/tasks/${task.id}/checkpoints`).then((response) => response.ok ? response.json() : []).then(setCheckpoints).catch(() => setCheckpoints([]));
  }, [task, isRddTask]);

  if (!task) {
    return null;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");

    try {
      const response = await apiFetch(`${API_BASE_URL}/api/tasks/${task.id}`, {
        method: "PUT",
        body: JSON.stringify({
          title,
          description,
          priority,
          dueDate: dueDate || null,
          assigneeId: assigneeId === "" ? null : Number(assigneeId),
          workload: Number(workload),
        }),
      });

      if (!response.ok) {
        throw new Error(response.status === 403
          ? "You do not have permission to perform this action."
          : "Unable to update task.");
      }

      onTaskUpdated();
      onClose();
    } catch (err) {
      console.error(err);
      setError("Unable to update task.");
    }
  }

  async function addCheckpoint(event) {
    event.preventDefault(); if (!checkpointTitle.trim()) return;
    const response = await apiFetch(`${API_BASE_URL}/api/tasks/${task.id}/checkpoints`, { method: "POST", body: JSON.stringify({ title: checkpointTitle.trim() }) });
    if (response.ok) { const created = await response.json(); setCheckpoints((items) => [...items, created]); setCheckpointTitle(""); }
  }
  async function updateCheckpoint(checkpoint, changes = {}) {
    const response = await apiFetch(`${API_BASE_URL}/api/tasks/${task.id}/checkpoints/${checkpoint.id}`, { method: "PUT", body: JSON.stringify({ title: checkpoint.title, description: checkpoint.description || null, dueDate: checkpoint.dueDate || null, status: checkpoint.status, position: checkpoint.position, ...changes }) });
    if (response.ok) { const updated = await response.json(); setCheckpoints((items) => items.map((item) => item.id === checkpoint.id ? updated : item)); }
  }
  async function deleteCheckpoint(checkpoint) {
    const response = await apiFetch(`${API_BASE_URL}/api/tasks/${task.id}/checkpoints/${checkpoint.id}`, { method: "DELETE" });
    if (response.ok) setCheckpoints((items) => items.filter((item) => item.id !== checkpoint.id));
  }
  function moveCheckpoint(checkpoint, delta) {
    const target = checkpoint.position + delta;
    if (target < 1 || target > checkpoints.length) return;
    void updateCheckpoint(checkpoint, { position: target });
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div
        className="modal-card"
        onMouseDown={(event) => event.stopPropagation()}
      >
          <div className="modal-header">
          <div>
            <h2>Edit Task</h2>
            <p>Update the task details without moving it.</p>
          </div>

        <RddTaskActions task={task} user={user} onChanged={onTaskUpdated} />

          <button
            type="button"
            className="modal-close-button"
            onClick={onClose}
            aria-label="Close dialog"
          >
            &times;
          </button>
        </div>

        <form className="create-task-form" onSubmit={handleSubmit}>
          <label htmlFor="edit-task-title">
            Title <span className="required-marker">*</span>
          </label>
          <input
            id="edit-task-title"
            type="text"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            required
          />

          <label htmlFor="edit-task-description">Description</label>
          <textarea
            id="edit-task-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows="4"
          />

          <div className="form-field-row">
            <div className="form-field">
              <label htmlFor="edit-task-priority">Priority</label>
              <select
                id="edit-task-priority"
                value={priority}
                onChange={(event) => setPriority(event.target.value)}
              >
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
              </select>
            </div>

            <div className="form-field">
              <label htmlFor="edit-task-due-date">Due Date</label>
              <input
                id="edit-task-due-date"
                type="date"
                value={dueDate}
                onChange={(event) => setDueDate(event.target.value)}
              />
            </div>
          </div>

          <label htmlFor="edit-task-workload">Workload</label>
          <select
            id="edit-task-workload"
            value={workload}
            onChange={(event) => setWorkload(event.target.value)}
          >
            <option value="1">1 — Very Small</option>
            <option value="2">2 — Small</option>
            <option value="3">3 — Medium</option>
            <option value="4">4 — Large</option>
            <option value="5">5 — Very Large</option>
          </select>

          <label htmlFor="edit-task-assignee">Assignee</label>
          <select
            id="edit-task-assignee"
            value={assigneeId}
            onChange={(event) => setAssigneeId(event.target.value)}
          >
            <option value="">Unassigned</option>
            {users.map((user) => (
              <option key={user.id} value={user.id}>
                {user.name || user.email}
              </option>
            ))}
          </select>

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

          {isRddTask && <section className="task-checkpoints"><div className="panel-heading"><h3>Checkpoints</h3><span>{checkpoints.length ? `${checkpoints.filter((item) => item.status === "COMPLETED").length} / ${checkpoints.length} · ${Math.round(checkpoints.filter((item) => item.status === "COMPLETED").length * 100 / checkpoints.length)}%` : "No checkpoints"}</span></div>{checkpoints.length > 0 && <p className="checkpoint-current">{(() => { const current = checkpoints.find((item) => item.status === "IN_PROGRESS") || checkpoints.find((item) => item.status === "PENDING"); return current ? `${current.status === "IN_PROGRESS" ? "Current" : "Next"}: ${current.title}` : "All checkpoints completed"; })()}</p>}{!checkpoints.length && <p className="checkpoint-empty">No checkpoints yet. Break this task into research milestones.</p>}<div className="checkpoint-add"><input aria-label="Checkpoint title" maxLength="180" placeholder="Add checkpoint" value={checkpointTitle} onChange={(event) => setCheckpointTitle(event.target.value)} /><button type="button" className="secondary-button" onClick={() => { if (checkpointTitle.trim()) void addCheckpoint({ preventDefault: () => {} }); }}>Add</button></div>{checkpoints.map((checkpoint, index) => <div className={`checkpoint-row ${checkpoint.dueState === "OVERDUE" ? "is-overdue" : ""}`} key={checkpoint.id}><button type="button" className={`checkpoint-toggle ${checkpoint.status === "COMPLETED" ? "is-complete" : ""}`} onClick={() => void updateCheckpoint(checkpoint, { status: checkpoint.status === "COMPLETED" ? "IN_PROGRESS" : "COMPLETED" })} aria-label={`Set ${checkpoint.title} status`}>{checkpoint.status === "COMPLETED" ? "✓" : checkpoint.status === "IN_PROGRESS" ? "●" : "○"}</button><div className="checkpoint-main"><input value={checkpoint.title} maxLength="180" aria-label="Checkpoint title" onChange={(event) => setCheckpoints((items) => items.map((item) => item.id === checkpoint.id ? { ...item, title: event.target.value } : item))} onBlur={() => void updateCheckpoint(checkpoint)} /><div className="checkpoint-details"><select value={checkpoint.status} onChange={(event) => void updateCheckpoint(checkpoint, { status: event.target.value })}><option value="PENDING">Pending</option><option value="IN_PROGRESS">In progress</option><option value="COMPLETED">Completed</option></select><input type="date" value={checkpoint.dueDate || ""} onChange={(event) => void updateCheckpoint(checkpoint, { dueDate: event.target.value || null })} />{checkpoint.dueState && <span className="checkpoint-due-state">{checkpoint.dueState.replaceAll("_", " ")}</span>}</div><textarea value={checkpoint.description || ""} placeholder="Description (optional)" maxLength="1000" onChange={(event) => setCheckpoints((items) => items.map((item) => item.id === checkpoint.id ? { ...item, description: event.target.value } : item))} onBlur={() => void updateCheckpoint(checkpoint)} /></div><div className="checkpoint-actions"><button type="button" onClick={() => moveCheckpoint(checkpoint, -1)} disabled={index === 0} aria-label="Move checkpoint up">↑</button><button type="button" onClick={() => moveCheckpoint(checkpoint, 1)} disabled={index === checkpoints.length - 1} aria-label="Move checkpoint down">↓</button><button type="button" onClick={() => void deleteCheckpoint(checkpoint)} aria-label={`Delete ${checkpoint.title}`}>×</button></div></div>)}</section>}

          <div className="modal-actions modal-actions-split">
            {canDelete && (
              <button
                type="button"
                className="delete-button"
                onClick={() => onDelete(task)}
              >
                Delete
              </button>
            )}

            <div className="modal-actions-right">
              <button
                type="button"
                className="cancel-button"
                onClick={onClose}
              >
                Cancel
              </button>
              <button type="submit" className="create-button">
                Save Changes
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}

export default EditTaskModal;
