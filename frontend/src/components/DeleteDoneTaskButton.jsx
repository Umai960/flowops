import { useState } from "react";
import { apiFetch } from "../api/apiFetch";

/** Permanent delete for finished tasks, shown to managers and admins only. */
export default function DeleteDoneTaskButton({ task, user, onDeleted }) {
  const [busy, setBusy] = useState(false);
  if (task?.status !== "DONE" || (user?.role !== "MANAGER" && user?.role !== "ADMIN")) return null;
  async function remove() {
    if (!window.confirm(`Delete "${task.title}" permanently? This cannot be undone and the task will also disappear from Completed Tasks and reports.`)) return;
    setBusy(true);
    try {
      const response = await apiFetch(`/api/tasks/${task.id}`, { method: "DELETE" });
      if (!response.ok) throw new Error(response.status === 403 ? "You do not have permission to delete this task." : "The task could not be deleted. Please try again.");
      await onDeleted?.(task.id);
    } catch (error) {
      window.alert(error.message);
    } finally {
      setBusy(false);
    }
  }
  return <div className="rdd-task-actions delete-done-action" onPointerDown={(event) => event.stopPropagation()} onClick={(event) => event.stopPropagation()}>
    <button type="button" className="danger-outline-button" disabled={busy} onClick={() => void remove()}>{busy ? "Deleting…" : "Delete"}</button>
  </div>;
}
