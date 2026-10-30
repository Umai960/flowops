import { useEffect, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import RddTaskActions from "../components/RddTaskActions";
import DeleteDoneTaskButton from "../components/DeleteDoneTaskButton";
import { taskColorClass } from "../components/TaskColorPicker";

const API_BASE_URL = "";
const compactDate = (value) => value ? new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short", timeZone: "Asia/Kuala_Lumpur" }).format(new Date(`${value}T00:00:00+08:00`)) : "";
const STATUSES = [
  { value: "DRAFT", label: "Draft" },
  { value: "DOING", label: "Doing" },
  { value: "REVIEW", label: "Review" },
  { value: "DONE", label: "Done" },
];

function StaffKanban({ staffUser, currentUser, refreshKey, onTaskSelected, onTaskChanged, onReassignTask }) {
  const [tasks, setTasks] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!staffUser) {
      return;
    }

    async function loadStaffTasks() {
      try {
        setError("");
        const response = await apiFetch(
          `${API_BASE_URL}/api/tasks/user/${staffUser.id}`
        );
        if (!response.ok) {
          throw new Error(`Failed to load staff tasks (${response.status})`);
        }
        const tasks = await response.json();
        const rddTasks = tasks.filter((task) => task.departmentName?.toUpperCase() === "RDD");
        const summaries = rddTasks.length ? await apiFetch(`${API_BASE_URL}/api/tasks/checkpoints/summary?${rddTasks.map((task) => `taskIds=${task.id}`).join("&")}`) : null;
        const summaryByTask = summaries?.ok ? Object.fromEntries((await summaries.json()).map((summary) => [summary.taskId, summary])) : {};
        setTasks(tasks.map((task) => ({ ...task, checkpointSummary: summaryByTask[task.id] })));
      } catch (loadError) {
        console.error("Failed to load staff Kanban:", loadError);
        setError("Unable to load this staff member's Kanban.");
      }
    }

    loadStaffTasks();
  }, [staffUser, refreshKey]);

  async function handleReviewAction(task, action) {
    try {
      const response = await apiFetch(
        `${API_BASE_URL}/api/tasks/${task.id}/review-action`,
        {
          method: "PUT",
          body: JSON.stringify({ action }),
        }
      );

      if (!response.ok) {
        throw new Error(`Review action failed (${response.status})`);
      }

      const updatedTask = await response.json();
      setTasks((currentTasks) => currentTasks.map((candidate) =>
        candidate.id === updatedTask.id ? updatedTask : candidate
      ));
      onTaskChanged?.();
    } catch (actionError) {
      console.error("Failed to apply review action:", actionError);
      setError("Unable to update the task review state.");
    }
  }

  return (
    <>
      <h1>{staffUser ? `${staffUser.name}'s Kanban` : "Staff Kanban"}</h1>

      {error && <p className="form-error" role="alert">{error}</p>}

      {!staffUser ? (
        <div className="empty-state">
          <p>Select a staff member to view their work.</p>
        </div>
      ) : (
        <div className="kanban-board personal-kanban staff-kanban">
          {STATUSES.map((status) => (
            <div className="kanban-column" key={status.value}>
              <div className="column-header">
                <h2>{status.label}</h2>
              </div>

              <div className="task-list">
                {tasks
                  .filter((task) => task.status === status.value)
                  .map((task) => (
                    <div
                      className={`task-card staff-task-card priority-card-${String(task.priority || "MEDIUM").toLowerCase()}${taskColorClass(task)}`}
                      key={task.id}
                      onClick={() => onTaskSelected(task)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          onTaskSelected(task);
                        }
                      }}
                    >
                      <h3 title={task.title}>{task.title}</h3>
                      <small className="task-board-name">{task.generalTask ? "GENERAL · PPC" : task.boardName}</small>
                      <p>{task.description}</p>
                      <div className={`task-priority-accent priority-${String(task.priority || "MEDIUM").toLowerCase()}`} title={`Priority: ${task.priority || "MEDIUM"}`} aria-label={`Priority: ${task.priority || "MEDIUM"}`} />
                      {task.workload != null && <small className="task-workload">Workload {task.workload}</small>}
                      <div className="task-card-footer"><span>{task.assigneeName || "Unassigned"}</span>{task.dueDate && <span>{compactDate(task.dueDate)}</span>}</div>
                      {task.checkpointSummary?.currentOrNextTitle && <small className="task-checkpoint-hint"><span>{task.checkpointSummary.currentOrNextLabel}</span><strong>{task.checkpointSummary.currentOrNextTitle}</strong></small>}
                      <RddTaskActions task={task} user={currentUser} onChanged={(updated) => setTasks((items) => items.map((item) => item.id === updated.id ? updated : item))} />
                      <DeleteDoneTaskButton task={task} user={currentUser} onDeleted={(id) => setTasks((items) => items.filter((item) => item.id !== id))} />

                      {status.value === "REVIEW" && (
                        <div className="review-actions" onClick={(event) => event.stopPropagation()}>
                          <button
                            type="button"
                            className="review-return-button"
                            onClick={() => handleReviewAction(task, "RETURN")}
                          >
                            Return for Changes
                          </button>
                          <button
                            type="button"
                            className="review-approve-button"
                            onClick={() => handleReviewAction(task, "APPROVE")}
                          >
                            Approve
                          </button>
                        </div>
                      )}
                      <button
                        type="button"
                        className="reassign-button"
                        onClick={(event) => {
                          event.stopPropagation();
                          onReassignTask(task);
                        }}
                      >
                        Reassign
                      </button>
                    </div>
                  ))}
                {tasks.filter((task) => task.status === status.value).length === 0 && (
                  <p className="column-empty">No tasks assigned.</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

export default StaffKanban;
