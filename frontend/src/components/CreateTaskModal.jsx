import { useEffect, useState } from "react";
import { apiFetch } from "../api/apiFetch";

const API_BASE_URL = "";

const INITIAL_FORM = {
  scope: "PROJECT",
  projectId: "",
  title: "",
  description: "",
  priority: "MEDIUM",
  dueDate: "",
  assigneeId: "",
  workload: "3",
};

function CreateTaskModal({ isOpen, column, board, boards = [], departmentId, generalOnly = false, canChooseGeneral = false, assignToSelf = false, users, user, onClose, onCreated }) {
  const [formData, setFormData] = useState(INITIAL_FORM);
  const [projectColumns, setProjectColumns] = useState([]);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setFormData((current) => ({
        ...current,
        scope: generalOnly || (!boards.length && canChooseGeneral) ? "GENERAL" : "PROJECT",
        projectId: String(board?.id ?? boards[0]?.id ?? ""),
        assigneeId: assignToSelf || user?.role === "STAFF" ? String(user.userId) : "",
      }));
    }
  }, [generalOnly, isOpen, user, board, boards, canChooseGeneral, assignToSelf]);

  useEffect(() => {
    if (!isOpen || generalOnly || !formData.projectId) return;
    let cancelled = false;
    apiFetch(`${API_BASE_URL}/api/columns/board/${formData.projectId}`)
      .then((response) => response.ok ? response.json() : [])
      .then((data) => { if (!cancelled) setProjectColumns(data); })
      .catch(() => { if (!cancelled) setProjectColumns([]); });
    return () => { cancelled = true; };
  }, [formData.projectId, generalOnly, isOpen]);

  if (!isOpen || (!generalOnly && !column && !boards.length && !canChooseGeneral)) {
    return null;
  }

  const isGeneral = generalOnly || formData.scope === "GENERAL";
  const generalDepartmentId = departmentId ?? board?.departmentId ?? user?.departmentId;
  const selectedBoard = boards.find((candidate) => candidate.id === Number(formData.projectId)) ?? board;
  const selectedColumn = column ?? projectColumns.find((candidate) => candidate.name.toLowerCase() === "to do");
  const eligibleUsers = isGeneral
    ? users.filter((candidate) => candidate.departmentId === generalDepartmentId)
    : users;

  function handleChange(event) {
    const { name, value } = event.target;

    setFormData((currentForm) => ({
      ...currentForm,
      [name]: value,
    }));
    setError("");
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");

    const title = formData.title.trim();

    if (!title) {
      setError("Title is required.");
      return;
    }

    if (!formData.assigneeId) {
      setError("An assignee is required.");
      return;
    }

    if (!isGeneral && !selectedColumn) {
      setError("The selected project does not have a To Do column.");
      return;
    }

    setIsSubmitting(true);

    try {
      const response = await apiFetch(`${API_BASE_URL}/api/tasks`, {
        method: "POST",
        body: JSON.stringify({
          title,
          description: formData.description.trim() || null,
          priority: formData.priority,
          dueDate: formData.dueDate || null,
          columnId: isGeneral ? null : selectedColumn?.id,
          departmentId: isGeneral ? Number(generalDepartmentId) : null,
          assigneeId: Number(formData.assigneeId),
          workload: Number(formData.workload),
        }),
      });

      if (!response.ok) {
        let message = `Task creation failed (${response.status}).`;

        try {
          const responseBody = await response.json();
          message = responseBody.message || responseBody.error || message;
        } catch {
          // Keep the status-based message when the server does not return JSON.
        }

        throw new Error(message);
      }

      const createdTask = await response.json();
      await onCreated(createdTask);
      setFormData(INITIAL_FORM);
      onClose();
    } catch (submitError) {
      console.error("Failed to create task:", submitError);
      setError(submitError.message || "Unable to create task. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleBackdropClick(event) {
    if (event.target === event.currentTarget && !isSubmitting) {
      onClose();
    }
  }

  return (
    <div
      className="modal-backdrop"
      role="presentation"
      onMouseDown={handleBackdropClick}
    >
      <div
        className="modal-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-task-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <h2 id="create-task-title">Create Task</h2>
            <p>{isGeneral ? `${user?.departmentName ?? "Department"} · General Task` : `Adding to ${selectedBoard?.name ?? "Project"} · To Do`}</p>
          </div>
          <button
            type="button"
            className="modal-close-button"
            onClick={onClose}
            disabled={isSubmitting}
            aria-label="Close dialog"
          >
            &times;
          </button>
        </div>

        <form className="create-task-form" onSubmit={handleSubmit}>
          {canChooseGeneral && !generalOnly && (
            <fieldset className="task-scope-fieldset">
              <legend>Task Scope</legend>
              <label><input type="radio" name="scope" value="PROJECT" checked={formData.scope === "PROJECT"} onChange={handleChange} /> Project task</label>
              <label><input type="radio" name="scope" value="GENERAL" checked={formData.scope === "GENERAL"} onChange={handleChange} /> General task</label>
            </fieldset>
          )}

          {!isGeneral && (
            <>
              <label htmlFor="task-project">Project</label>
              <select id="task-project" name="projectId" value={formData.projectId} onChange={handleChange} required>
                <option value="">Select a project</option>
                {boards.map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.name}</option>)}
              </select>
            </>
          )}

          <label htmlFor="task-title">
            Title <span className="required-marker">*</span>
          </label>
          <input
            id="task-title"
            name="title"
            type="text"
            value={formData.title}
            onChange={handleChange}
            placeholder="Enter a task title"
            required
            autoFocus
          />

          <label htmlFor="task-description">Description</label>
          <textarea
            id="task-description"
            name="description"
            value={formData.description}
            onChange={handleChange}
            placeholder="Add more details (optional)"
            rows="4"
          />

          <div className="form-field-row">
            <div className="form-field">
              <label htmlFor="task-priority">Priority</label>
              <select
                id="task-priority"
                name="priority"
                value={formData.priority}
                onChange={handleChange}
              >
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
              </select>
            </div>

            <div className="form-field">
              <label htmlFor="task-due-date">Due Date</label>
              <input
                id="task-due-date"
                name="dueDate"
                type="date"
                value={formData.dueDate}
                onChange={handleChange}
              />
            </div>
          </div>

          {!assignToSelf && <>
          <label htmlFor="task-workload">Workload</label>
          <select
            id="task-workload"
            name="workload"
            value={formData.workload}
            onChange={handleChange}
          >
            <option value="1">1 — Very Small</option>
            <option value="2">2 — Small</option>
            <option value="3">3 — Medium</option>
            <option value="4">4 — Large</option>
            <option value="5">5 — Very Large</option>
          </select>

          <label htmlFor="task-assignee">Assignee <span className="required-marker">*</span></label>
          <select
            id="task-assignee"
            name="assigneeId"
            value={formData.assigneeId}
            onChange={handleChange}
          >
            {user?.role !== "STAFF" && <option value="">Select an assignee</option>}
            {eligibleUsers.map((user) => (
              <option key={user.id} value={user.id}>
                {user.name || user.email}
              </option>
            ))}
          </select>

          {user?.role === "STAFF" && (
            <small className="form-help">Created by {user.name || user.email}. Staff can assign to themselves or same-department staff.</small>
          )}
          </>}

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

          <div className="modal-actions">
            <button
              type="button"
              className="cancel-button"
              onClick={onClose}
              disabled={isSubmitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="create-button"
              disabled={isSubmitting}
            >
              {isSubmitting ? "Creating..." : "Create Task"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default CreateTaskModal;
