import { useEffect, useRef, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import CreateTaskModal from "../components/CreateTaskModal";
import RddTaskActions from "../components/RddTaskActions";

const API_BASE_URL = "";
const DRAG_START_THRESHOLD = 6;
const compactDate = (value) => value ? new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short", timeZone: "Asia/Kuala_Lumpur" }).format(new Date(`${value}T00:00:00+08:00`)) : "";

const STATUSES = [
  { value: "DRAFT", label: "Draft" },
  { value: "DOING", label: "Doing" },
  { value: "REVIEW", label: "Review" },
  { value: "DONE", label: "Done" },
];

function PersonalKanban({ user, users = [], departments = [] }) {
  const [tasks, setTasks] = useState([]);
  const [draggedTaskId, setDraggedTaskId] = useState(null);
  const [dropIndicator, setDropIndicator] = useState(null);
  const [dragPreview, setDragPreview] = useState(null);
  const [showCreateGeneral, setShowCreateGeneral] = useState(false);
  const tasksRef = useRef(tasks);
  const pointerDownRef = useRef(null);
  const draggedTaskRef = useRef(null);
  const dropIndicatorRef = useRef(null);
  const userDepartment = departments.find((department) => department.id === user?.departmentId);
  const canCreateGeneral = Boolean(userDepartment || user?.role === "ADMIN");

  async function loadMyTasks() {
    try {
      const response = await apiFetch(`${API_BASE_URL}/api/tasks/my`);

      if (!response.ok) {
        throw new Error("Failed to load personal tasks");
      }

      const tasks = await response.json();
      const rddTasks = tasks.filter((task) => task.departmentName?.toUpperCase() === "RDD");
      const summaries = rddTasks.length ? await apiFetch(`${API_BASE_URL}/api/tasks/checkpoints/summary?${rddTasks.map((task) => `taskIds=${task.id}`).join("&")}`) : null;
      const summaryByTask = summaries?.ok ? Object.fromEntries((await summaries.json()).map((summary) => [summary.taskId, summary])) : {};
      setTasks(tasks.map((task) => ({ ...task, checkpointSummary: summaryByTask[task.id] })));
    } catch (error) {
      console.error("Failed to load personal Kanban:", error);
    }
  }

  useEffect(() => {
    loadMyTasks();
  }, []);

  useEffect(() => {
    tasksRef.current = tasks;
  }, [tasks]);

  function setIndicator(indicator) {
    dropIndicatorRef.current = indicator;
    setDropIndicator(indicator);
  }

  function clearDragState() {
    pointerDownRef.current = null;
    draggedTaskRef.current = null;
    dropIndicatorRef.current = null;
    setDraggedTaskId(null);
    setDropIndicator(null);
    setDragPreview(null);
  }

  function getDropIndicator(clientX, clientY, task) {
    const element = document.elementFromPoint(clientX, clientY);
    const taskElement = element?.closest("[data-personal-task-id]");

    if (taskElement) {
      const targetTaskId = Number(taskElement.dataset.personalTaskId);
      if (targetTaskId === task.id) {
        return null;
      }

      const rect = taskElement.getBoundingClientRect();
      if (user?.role === "STAFF" && !task.generalTask && taskElement.dataset.personalStatus === "DONE") {
        return null;
      }
      return {
        status: taskElement.dataset.personalStatus,
        taskId: targetTaskId,
        position: clientY < rect.top + rect.height / 2 ? "before" : "after",
      };
    }

    const columnElement = element?.closest("[data-personal-status]");
    if (
      user?.role === "STAFF" &&
      !task.generalTask &&
      columnElement?.dataset.personalStatus === "DONE"
    ) {
      return null;
    }
    return columnElement
      ? {
          status: columnElement.dataset.personalStatus,
          taskId: null,
          position: "after",
        }
      : null;
  }

  function handlePointerDown(event, task) {
    if (event.button !== 0) {
      return;
    }

    pointerDownRef.current = {
      task,
      startX: event.clientX,
      startY: event.clientY,
      offsetX: event.clientX - event.currentTarget.getBoundingClientRect().left,
      offsetY: event.clientY - event.currentTarget.getBoundingClientRect().top,
      width: event.currentTarget.getBoundingClientRect().width,
    };
  }

  useEffect(() => {
    function handlePointerMove(event) {
      const pointerDown = pointerDownRef.current;
      let task = draggedTaskRef.current;

      if (!pointerDown) {
        return;
      }

      if (!task) {
        const distanceX = Math.abs(event.clientX - pointerDown.startX);
        const distanceY = Math.abs(event.clientY - pointerDown.startY);
        if (Math.max(distanceX, distanceY) < DRAG_START_THRESHOLD) {
          return;
        }

        task = pointerDown.task;
        draggedTaskRef.current = task;
        setDraggedTaskId(task.id);
        setDragPreview({
          task,
          x: event.clientX,
          y: event.clientY,
          offsetX: pointerDown.offsetX,
          offsetY: pointerDown.offsetY,
          width: pointerDown.width,
        });
      }

      setDragPreview((currentPreview) =>
        currentPreview
          ? { ...currentPreview, x: event.clientX, y: event.clientY }
          : currentPreview
      );

      setIndicator(getDropIndicator(event.clientX, event.clientY, task));
    }

    async function handlePointerUp() {
      const task = draggedTaskRef.current;
      const indicator = dropIndicatorRef.current;

      if (!task) {
        clearDragState();
        return;
      }

      clearDragState();

      if (!indicator) {
        return;
      }

      const targetTasks = tasksRef.current
        .filter((candidate) => candidate.status === indicator.status)
        .filter((candidate) => candidate.id !== task.id);

      const targetIndex = indicator.taskId === null
        ? targetTasks.length
        : targetTasks.findIndex(
            (candidate) => candidate.id === indicator.taskId
          ) + (indicator.position === "after" ? 1 : 0);

      try {
        const response = await apiFetch(`${API_BASE_URL}/api/tasks/${task.id}/status`, {
          method: "PUT",
          body: JSON.stringify({
            status: indicator.status,
            targetPosition: targetIndex + 1,
          }),
        });

        if (!response.ok) {
          throw new Error(`Failed to move personal task (${response.status})`);
        }

        await loadMyTasks();
      } catch (error) {
        console.error("Failed to move personal task:", error);
      }
    }

    function handlePointerCancel() {
      clearDragState();
    }

    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
    window.addEventListener("pointercancel", handlePointerCancel);

    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
      window.removeEventListener("pointercancel", handlePointerCancel);
    };
  }, [user]);

  return (
    <>
      <div className="personal-kanban-heading">
        <h1>My Work</h1>
        {canCreateGeneral && userDepartment && (
          <button type="button" className="primary-button" onClick={() => setShowCreateGeneral(true)}>+ General Task</button>
        )}
      </div>

      <div className="kanban-board personal-kanban">
        {STATUSES.map((status) => {
          const statusTasks = tasks.filter(
            (task) => task.status === status.value
          );

          return (
            <div
              className="kanban-column"
              key={status.value}
              data-personal-status={status.value}
            >
              <div className="column-header">
                <h2>{status.label}</h2>
              </div>

              <div className="task-list">
                {statusTasks.map((task) => (
                  <div className="task-wrapper" key={task.id}>
                    {dropIndicator?.status === status.value &&
                      dropIndicator?.taskId === task.id &&
                      dropIndicator?.position === "before" && (
                        <div className="drop-indicator" />
                      )}

                    <div
                      className={`task-card priority-card-${String(task.priority || "MEDIUM").toLowerCase()} ${
                        draggedTaskId === task.id ? "task-card--dragging" : ""
                      }`}
                      data-personal-task-id={task.id}
                      data-personal-status={status.value}
                      onPointerDown={(event) => handlePointerDown(event, task)}
                    >
                      <h3 title={task.title}>{task.title}</h3>
                      <small className="task-board-name">{task.generalTask ? "GENERAL · PPC" : task.boardName}</small>
                      <p>{task.description}</p>
                      <div className={`task-priority-accent priority-${String(task.priority || "MEDIUM").toLowerCase()}`} title={`Priority: ${task.priority || "MEDIUM"}`} aria-label={`Priority: ${task.priority || "MEDIUM"}`} />
                      {task.workload != null && <small className="task-workload">Workload {task.workload}</small>}
                      <div className="task-card-footer"><span>{task.assigneeName || "Unassigned"}</span>{task.dueDate && <span>{compactDate(task.dueDate)}</span>}</div>
                      {task.checkpointSummary?.currentOrNextTitle && <small className="task-checkpoint-hint"><span>{task.checkpointSummary.currentOrNextLabel}</span><strong>{task.checkpointSummary.currentOrNextTitle}</strong></small>}
                      <RddTaskActions task={task} user={user} onChanged={loadMyTasks} />
                    </div>

                    {dropIndicator?.status === status.value &&
                      dropIndicator?.taskId === task.id &&
                      dropIndicator?.position === "after" && (
                        <div className="drop-indicator" />
                      )}
                  </div>
                ))}

                {dropIndicator?.status === status.value &&
                  dropIndicator?.taskId === null && (
                    <div className="drop-indicator" />
                  )}
              </div>
            </div>
          );
        })}
      </div>

      {dragPreview && (
        <div
          className="drag-preview"
          style={{
            left: `${dragPreview.x - dragPreview.offsetX}px`,
            top: `${dragPreview.y - dragPreview.offsetY}px`,
            width: `${dragPreview.width}px`,
          }}
        >
          <div className={`task-card drag-preview-card priority-card-${String(dragPreview.task.priority || "MEDIUM").toLowerCase()}`}>
            <h3 title={dragPreview.task.title}>{dragPreview.task.title}</h3>
            <small className="task-board-name">{dragPreview.task.generalTask ? "GENERAL · PPC" : dragPreview.task.boardName}</small>
            <p>{dragPreview.task.description}</p>
            <div className={`task-priority-accent priority-${String(dragPreview.task.priority || "MEDIUM").toLowerCase()}`} title={`Priority: ${dragPreview.task.priority || "MEDIUM"}`} aria-label={`Priority: ${dragPreview.task.priority || "MEDIUM"}`} />
            {dragPreview.task.workload != null && <small className="task-workload">Workload {dragPreview.task.workload}</small>}
            <div className="task-card-footer"><span>{dragPreview.task.assigneeName || "Unassigned"}</span>{dragPreview.task.dueDate && <span>{compactDate(dragPreview.task.dueDate)}</span>}</div>
          </div>
        </div>
      )}

      <CreateTaskModal
        isOpen={showCreateGeneral}
        generalOnly
        departmentId={userDepartment?.id}
        users={users}
        user={user}
        onClose={() => setShowCreateGeneral(false)}
        onCreated={loadMyTasks}
      />
    </>
  );
}

export default PersonalKanban;
