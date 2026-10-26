import { useCallback, useEffect, useRef, useState } from "react";
import "./App.css";
import CreateTaskModal from "./components/CreateTaskModal";
import CreateBoardModal from "./components/CreateBoardModal";
import ConfirmDeleteModal from "./components/ConfirmDeleteModal";
import ConfirmDeleteBoardModal from "./components/ConfirmDeleteBoardModal";
import EditBoardModal from "./components/EditBoardModal";
import EditTaskModal from "./components/EditTaskModal";
import LoginPage from "./components/LoginPage";
import AppShell from "./components/AppShell";
import PersonalKanban from "./pages/PersonalKanban";
import StaffKanban from "./pages/StaffKanban";
import ManagerDashboard from "./pages/ManagerDashboard";
import StaffDashboard from "./pages/StaffDashboard";
import AdminDashboard from "./pages/AdminDashboard";
import ReviewQueuePage from "./pages/ReviewQueuePage";
import HistoryPage from "./pages/HistoryPage";
import ReassignTaskModal from "./components/ReassignTaskModal";
import DailyReportPage from "./pages/DailyReportPage";
import TeamDailyReportsPage from "./pages/TeamDailyReportsPage";
import MonthlyReportsPage from "./pages/MonthlyReportsPage";
import UserManagementPage from "./pages/UserManagementPage";
import DataManagementPage from "./pages/DataManagementPage";
import ClientAccessPage from "./pages/ClientAccessPage";
import ActivationPage from "./pages/ActivationPage";
import PpcPlanningPage from "./pages/PpcPlanningPage";
import RawMaterialArrivalsPage from "./pages/RawMaterialArrivalsPage";
import DesktopNotificationsPage from "./pages/DesktopNotificationsPage";
import CompletedTasksPage from "./pages/CompletedTasksPage";
import InterdepartmentRequestsPage from "./pages/InterdepartmentRequestsPage";
import RddTaskActions from "./components/RddTaskActions";
import { apiFetch } from "./api/apiFetch";
import { useAuth } from "./context/AuthContext";

const API_BASE_URL = "";
const DRAG_START_THRESHOLD = 6;

function viewForLocation() {
  const path = window.location.pathname;
  const hasTaskTarget = path === "/" && new URLSearchParams(window.location.search).has("taskId");
  if (path === "/settings/desktop-notifications") return "desktop-notifications";
  if (path === "/completed") return "completed";
  if (path === "/requests" || path.startsWith("/requests/")) return "requests";
  if (path === "/ppc/planning") return "ppc-planning";
  if (path === "/ppc/raw-material-arrivals") return "ppc-arrivals";
  if (path === "/reviews") return "reviews";
  if (path === "/projects") return "project";
  if (path.startsWith("/reports")) return "report";
  if (path === "/admin/users") return "users-admin";
  if (path === "/admin/settings/data-management") return "data-management";
  if (path === "/admin/settings/client-access") return "client-access";
  return hasTaskTarget ? "personal" : "dashboard";
}

function formatCompactDate(value) {
  if (!value) return "";
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short", timeZone: "Asia/Kuala_Lumpur" }).format(new Date(`${value}T00:00:00+08:00`));
}

function TaskCardContent({ task, showBoardName = true }) {
  return (
    <>
      <h3 title={task.title}>{task.title}</h3>

      <p>{task.description}</p>

      {showBoardName && <small className="task-board-name">{task.generalTask ? "General Task" : task.boardName}</small>}

      <div className={`task-priority-accent priority-${String(task.priority || "MEDIUM").toLowerCase()}`} title={`Priority: ${task.priority || "MEDIUM"}`} aria-label={`Priority: ${task.priority || "MEDIUM"}`} />

      {task.workload != null && <small className="task-workload">Workload {task.workload}</small>}

      <div className="task-card-footer"><span>{task.assigneeName || "Unassigned"}</span>{task.dueDate && <span>{formatCompactDate(task.dueDate)}</span>}</div>

      {task.departmentName?.toUpperCase() === "RDD" && task.checkpointSummary?.currentOrNextTitle && <small className="task-checkpoint-hint"><span>{task.checkpointSummary.currentOrNextLabel}</span><strong>{task.checkpointSummary.currentOrNextTitle}</strong></small>}
    </>
  );
}

function App() {
  const { user, logout, loading, isAuthenticated } = useAuth();
  const isAdmin = user?.role === "ADMIN";
  const isManager = user?.role === "MANAGER";
  const canManageBoards = isAdmin || isManager;
  const canDeleteTask = isAdmin || isManager;
  const initialView = viewForLocation();
  const [activeView, setActiveView] = useState(initialView);
  const [selectedStaffId, setSelectedStaffId] = useState("");
  const [staffRefreshKey, setStaffRefreshKey] = useState(0);
  const [selectedReportUserId, setSelectedReportUserId] = useState(null);
  const [selectedReportDate, setSelectedReportDate] = useState(null);
  const [departments, setDepartments] = useState([]);
  const [selectedDepartmentId, setSelectedDepartmentId] = useState(null);
  const [boards, setBoards] = useState([]);
  const [selectedBoardId, setSelectedBoardId] = useState(null);
  const routeBoardId = Number(new URLSearchParams(window.location.search).get("boardId")) || null;
  const [showCreateBoard, setShowCreateBoard] = useState(false);
  const [boardToEdit, setBoardToEdit] = useState(null);
  const [boardToDelete, setBoardToDelete] = useState(null);
  const [deletingBoard, setDeletingBoard] = useState(false);
  const [deleteBoardError, setDeleteBoardError] = useState("");

  const [columns, setColumns] = useState([]);
  const [tasksByColumn, setTasksByColumn] = useState({});
  const [generalTasks, setGeneralTasks] = useState([]);
  const [users, setUsers] = useState([]);
  const [selectedColumn, setSelectedColumn] = useState(null);
  const [showCreateTaskModal, setShowCreateTaskModal] = useState(false);
  const [selectedTask, setSelectedTask] = useState(null);
  const [taskToReassign, setTaskToReassign] = useState(null);
  const [taskToDelete, setTaskToDelete] = useState(null);
  const [deletingTask, setDeletingTask] = useState(false);
  const [draggedTask, setDraggedTask] = useState(null);
  const [dropIndicator, setDropIndicator] = useState(null);
  const [dragPreview, setDragPreview] = useState(null);
  const [, setPermissionMessage] = useState("");
  const draggedTaskRef = useRef(null);
  const dropIndicatorRef = useRef(null);
  const pointerDownRef = useRef(null);
  const tasksByColumnRef = useRef(tasksByColumn);
  const generalTasksRef = useRef(generalTasks);
  const moveTaskRef = useRef(null);
  const moveGeneralTaskRef = useRef(null);
  const selectedStaff = users.find(
    (candidate) => candidate.id === Number(selectedStaffId)
  ) ?? null;
  const canShowGeneralBoard = selectedDepartmentId !== null;

  const loadBoard = useCallback(async (boardId) => {
    try {
      const columnsResponse = await apiFetch(
        `${API_BASE_URL}/api/columns/board/${boardId}`
      );

      if (!columnsResponse.ok) {
        if (columnsResponse.status === 403) {
          setPermissionMessage("You do not have permission to access this department.");
        }
        throw new Error(`Columns request failed (${columnsResponse.status}).`);
      }

      const columnData = await columnsResponse.json();
      setColumns(columnData);

      const taskEntries = await Promise.all(
        columnData.map(async (column) => {
          const taskResponse = await apiFetch(
            `${API_BASE_URL}/api/tasks/column/${column.id}`
          );

          if (!taskResponse.ok) {
            throw new Error(
              `Tasks request failed for column ${column.id} (${taskResponse.status}).`
            );
          }

          const tasks = await taskResponse.json();

          return [column.id, tasks];
        })
      );

      const allTasks = taskEntries.flatMap(([, tasks]) => tasks);
      const summaries = allTasks.length ? await apiFetch(`${API_BASE_URL}/api/tasks/checkpoints/summary?${allTasks.map((task) => `taskIds=${task.id}`).join("&")}`) : null;
      const summaryByTask = summaries?.ok ? Object.fromEntries((await summaries.json()).map((summary) => [summary.taskId, summary])) : {};
      setTasksByColumn(Object.fromEntries(taskEntries.map(([columnId, tasks]) => [columnId, tasks.map((task) => ({ ...task, checkpointSummary: summaryByTask[task.id] }))])));
    } catch (error) {
      console.error("Failed to load board:", error);
    }
  }, []);

  const loadGeneralTasks = useCallback(async (departmentId) => {
    try {
      const response = await apiFetch(`${API_BASE_URL}/api/tasks/department/${departmentId}`);
      if (!response.ok) throw new Error(`General tasks request failed (${response.status}).`);
      const data = await response.json();
      const tasks = data.filter((task) => task.generalTask);
      const summaries = tasks.length ? await apiFetch(`${API_BASE_URL}/api/tasks/checkpoints/summary?${tasks.map((task) => `taskIds=${task.id}`).join("&")}`) : null;
      const summaryByTask = summaries?.ok ? Object.fromEntries((await summaries.json()).map((summary) => [summary.taskId, summary])) : {};
      setGeneralTasks(tasks.map((task) => ({ ...task, checkpointSummary: summaryByTask[task.id] })));
    } catch (error) {
      console.error("Failed to load general tasks:", error);
      setGeneralTasks([]);
    }
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !user) {
      return;
    }

    // Permission errors are handled silently in the workspace. Clear any
    // stale message when switching accounts or roles.
    setPermissionMessage("");
    setActiveView(viewForLocation());
  }, [isAuthenticated, user]);

  useEffect(() => {
    if (!isAuthenticated) {
      setUsers([]);
      return;
    }

    async function loadDepartments() {
      try {
        const response = await apiFetch(`${API_BASE_URL}/api/departments`);

        if (!response.ok) {
          throw new Error(`Departments request failed (${response.status}).`);
        }

        const data = await response.json();

        setDepartments(data);

        if (data.length > 0) {
          setSelectedDepartmentId(isAdmin ? data[0].id : user.departmentId);
        }
      } catch (error) {
        console.error("Failed to load departments:", error);
      }
    }

    loadDepartments();
  }, [isAuthenticated, isAdmin, user]);

  useEffect(() => {
    if (!user) {
      return;
    }

    if (!isAdmin) {
      setSelectedDepartmentId(user.departmentId);
    }
  }, [user, isAdmin]);

  const loadBoards = useCallback(async (departmentId, preferredBoardId = null) => {
    try {
      const response = await apiFetch(
        `${API_BASE_URL}/api/boards/department/${departmentId}`
      );

      if (!response.ok) {
        if (response.status === 403) {
          setPermissionMessage("You do not have permission to access this department.");
        }
        throw new Error(`Boards request failed (${response.status}).`);
      }

      const data = await response.json();

      setBoards(data);

      if (data.length > 0) {
        const selectedBoardStillExists = data.some(
          (board) => board.id === preferredBoardId
        );
        setSelectedBoardId(
          selectedBoardStillExists ? preferredBoardId : data[0].id
        );
      } else {
        setSelectedBoardId(null);
        setColumns([]);
        setTasksByColumn({});
      }
      if (data.length === 0) {
        setSelectedBoardId("general");
      }
    } catch (error) {
      console.error("Failed to load boards:", error);
    }
  }, [departments]);

  useEffect(() => {
    if (selectedDepartmentId !== null) {
      loadBoards(selectedDepartmentId, activeView === "project" ? routeBoardId : null);
    }
  }, [activeView, selectedDepartmentId, loadBoards, routeBoardId]);

  useEffect(() => {
    if (selectedBoardId === "general") {
      loadGeneralTasks(selectedDepartmentId);
      setColumns([]);
      setTasksByColumn({});
    } else if (selectedBoardId !== null) {
      loadBoard(selectedBoardId);
      return;
    }

    setColumns([]);
    setTasksByColumn({});
  }, [activeView, selectedBoardId, selectedDepartmentId, loadBoard, loadGeneralTasks]);

  useEffect(() => {
    if (!isAuthenticated || selectedBoardId === null || selectedBoardId === "general" || !["project", "staff"].includes(activeView)) return undefined;
    const refresh = () => { if (document.visibilityState === "visible") loadBoard(selectedBoardId); };
    const interval = window.setInterval(refresh, 20000);
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [activeView, isAuthenticated, selectedBoardId, loadBoard]);

  useEffect(() => {
    if (!isAuthenticated) {
      setUsers([]);
      return;
    }

    async function loadAssignableUsers() {
      try {
        const response = await apiFetch(`${API_BASE_URL}/api/users/task-assignees`);

        if (!response.ok) {
          throw new Error(`Assignable users request failed (${response.status}).`);
        }

        setUsers(await response.json());
      } catch (error) {
        console.error("Failed to load assignable users:", error);
      }
    }

    if (user) {
      loadAssignableUsers();
    }
  }, [isAuthenticated, user]);

  useEffect(() => {
    tasksByColumnRef.current = tasksByColumn;
  }, [tasksByColumn]);

  useEffect(() => {
    generalTasksRef.current = generalTasks;
  }, [generalTasks]);

  function updateDropIndicator(indicator) {
    const previous = dropIndicatorRef.current;
    const isSameIndicator =
      previous?.columnId === indicator?.columnId &&
      previous?.taskId === indicator?.taskId &&
      previous?.position === indicator?.position;

    if (isSameIndicator) {
      return;
    }

    dropIndicatorRef.current = indicator;
    setDropIndicator(indicator);
  }

  function clearDragState() {
    draggedTaskRef.current = null;
    dropIndicatorRef.current = null;
    pointerDownRef.current = null;
    setDraggedTask(null);
    setDropIndicator(null);
    setDragPreview(null);
  }

  function getDropIndicator(clientX, clientY, task) {
    const element = document.elementFromPoint(clientX, clientY);
    const generalTaskElement = element?.closest("[data-general-task-id]");
    const generalColumnElement = element?.closest("[data-general-status]");

    if (task.generalTask) {
      if (generalTaskElement) {
        const targetTaskId = Number(generalTaskElement.dataset.generalTaskId);
        if (targetTaskId === task.id) return null;
        const rect = generalTaskElement.getBoundingClientRect();
        return {
          status: generalTaskElement.dataset.generalStatus,
          taskId: targetTaskId,
          position: clientY < rect.top + rect.height / 2 ? "before" : "after",
        };
      }
      return generalColumnElement ? {
        status: generalColumnElement.dataset.generalStatus,
        taskId: null,
        position: "after",
      } : null;
    }

    const taskElement = element?.closest("[data-task-id]");

    if (taskElement) {
      const targetTaskId = Number(taskElement.dataset.taskId);
      const targetColumnId = Number(taskElement.dataset.columnId);

      if (targetTaskId === task.id) {
        return null;
      }

      const rect = taskElement.getBoundingClientRect();

      return {
        columnId: targetColumnId,
        taskId: targetTaskId,
        position: clientY < rect.top + rect.height / 2 ? "before" : "after",
      };
    }

    const columnElement = element?.closest("[data-column-id]");

    if (!columnElement) {
      return null;
    }

    return {
      columnId: Number(columnElement.dataset.columnId),
      taskId: null,
      position: "after",
    };
  }

  function handlePointerDown(event, task) {
    if (event.button !== 0) {
      return;
    }

    const card = event.currentTarget;
    const rect = card.getBoundingClientRect();
    pointerDownRef.current = {
      task,
      canDrag: true,
      startX: event.clientX,
      startY: event.clientY,
      offsetX: event.clientX - rect.left,
      offsetY: event.clientY - rect.top,
      width: rect.width,
    };
  }

  function shouldStartDrag(pointerDown, event) {
    const distanceX = Math.abs(event.clientX - pointerDown.startX);
    const distanceY = Math.abs(event.clientY - pointerDown.startY);

    return Math.max(distanceX, distanceY) >= DRAG_START_THRESHOLD;
  }

  function startDrag(pointerDown, event) {
    draggedTaskRef.current = pointerDown.task;
    dropIndicatorRef.current = null;
    setDraggedTask(pointerDown.task);
    setDropIndicator(null);
    setDragPreview({
      task: pointerDown.task,
      x: event.clientX,
      y: event.clientY,
      offsetX: pointerDown.offsetX,
      offsetY: pointerDown.offsetY,
      width: pointerDown.width,
    });
  }

  useEffect(() => {
    function handlePointerMove(event) {
      const pointerDown = pointerDownRef.current;
      const task = draggedTaskRef.current;

      if (!pointerDown || !pointerDown.canDrag) {
        return;
      }

      if (!task) {
        if (!shouldStartDrag(pointerDown, event)) {
          return;
        }

        startDrag(pointerDown, event);
        return;
      }

      setDragPreview((currentPreview) =>
        currentPreview
          ? {
              ...currentPreview,
              x: event.clientX,
              y: event.clientY,
            }
          : currentPreview
      );

      updateDropIndicator(getDropIndicator(event.clientX, event.clientY, task));
    }

    async function handlePointerUp() {
      const task = draggedTaskRef.current;
      const indicator = dropIndicatorRef.current;
      const pointerDown = pointerDownRef.current;

      if (!task) {
        if (pointerDown) {
          clearDragState();
          setSelectedTask(pointerDown.task);
        }

        return;
      }

      clearDragState();

      if (!indicator) {
        return;
      }

      if (task.generalTask) {
        const targetTasks = generalTasksRef.current
          .filter((candidate) => candidate.status === indicator.status)
          .filter((candidate) => candidate.id !== task.id);
        const targetIndex = indicator.taskId === null
          ? targetTasks.length
          : targetTasks.findIndex((candidate) => candidate.id === indicator.taskId)
            + (indicator.position === "after" ? 1 : 0);
        await moveGeneralTaskRef.current(task, indicator.status, targetIndex + 1);
        return;
      }

      if (indicator.taskId === null) {
        const targetTasks = tasksByColumnRef.current[indicator.columnId] || [];

        await moveTaskRef.current(
          task,
          indicator.columnId,
          targetTasks.length + 1
        );
        return;
      }

      const targetTask = (
        tasksByColumnRef.current[indicator.columnId] || []
      ).find((candidate) => candidate.id === indicator.taskId);

      if (!targetTask) {
        return;
      }

      let targetPosition =
        targetTask.position + (indicator.position === "after" ? 1 : 0);

      if (
        targetTask.columnId === task.columnId &&
        targetTask.position > task.position
      ) {
        targetPosition -= 1;
      }

      if (
        targetTask.columnId === task.columnId &&
        targetPosition === task.position
      ) {
        return;
      }

      await moveTaskRef.current(task, indicator.columnId, targetPosition);
    }

    function handlePointerCancel() {
      if (draggedTaskRef.current) {
        clearDragState();
      }
    }

    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
    window.addEventListener("pointercancel", handlePointerCancel);

    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
      window.removeEventListener("pointercancel", handlePointerCancel);
    };
  }, []);

  const moveGeneralTask = useCallback(async (task, status, targetPosition) => {
    try {
      const response = await apiFetch(`${API_BASE_URL}/api/tasks/${task.id}/status`, {
        method: "PUT",
        body: JSON.stringify({ status, targetPosition }),
      });
      if (!response.ok) throw new Error(`Failed to move general task (${response.status})`);
      await loadGeneralTasks(selectedDepartmentId);
    } catch (error) {
      console.error("Failed to move general task:", error);
    }
  }, [loadGeneralTasks, selectedDepartmentId]);
  useEffect(() => {
    moveGeneralTaskRef.current = moveGeneralTask;
  }, [moveGeneralTask]);

  const moveTask = useCallback(
    async (task, targetColumnId, targetPosition) => {
      try {
        const response = await apiFetch(`${API_BASE_URL}/api/tasks/${task.id}/move`, {
          method: "PUT",
          body: JSON.stringify({
            targetColumnId,
            targetPosition,
          }),
        });

        if (!response.ok) {
          if (response.status === 403) {
            setPermissionMessage("You do not have permission to perform this action.");
          }
          throw new Error("Failed to move task");
        }

        setDraggedTask(null);
        setDropIndicator(null);
        if (selectedBoardId !== null) {
          await loadBoard(selectedBoardId);
        }
      } catch (error) {
        console.error("Failed to move task:", error);
      }
    },
    [loadBoard, selectedBoardId]
  );

  useEffect(() => {
    moveTaskRef.current = moveTask;
  }, [moveTask]);

  function openCreateTaskModal(column) {
    setSelectedColumn(column);
    setShowCreateTaskModal(true);
  }

  function closeCreateTaskModal() {
    setSelectedColumn(null);
    setShowCreateTaskModal(false);
  }

  async function handleBoardCreated(createdBoard) {
    setSelectedDepartmentId(createdBoard.departmentId);
    await loadBoards(createdBoard.departmentId, createdBoard.id);
  }

  function handleDepartmentCreated(department) {
    setDepartments((current) => [...current, department].sort((left, right) => left.name.localeCompare(right.name)));
  }

  async function handleBoardUpdated(updatedBoard) {
    await loadBoards(updatedBoard.departmentId, updatedBoard.id);
  }

  function requestDeleteBoard(board) {
    setDeleteBoardError("");
    setBoardToDelete(board);
  }

  function cancelDeleteBoard() {
    setBoardToDelete(null);
    setDeleteBoardError("");
  }

  async function confirmDeleteBoard() {
    if (!boardToDelete) {
      return;
    }

    setDeletingBoard(true);
    setDeleteBoardError("");

    try {
      const response = await apiFetch(
        `${API_BASE_URL}/api/boards/${boardToDelete.id}`,
        {
          method: "DELETE",
        }
      );

      if (!response.ok) {
        if (response.status === 400) {
          const data = await response.json().catch(() => null);
          setDeleteBoardError(
            data?.message || "Cannot delete board because it still contains tasks"
          );
          return;
        }

        if (response.status === 403) {
          setPermissionMessage("You do not have permission to delete this board.");
        }

        throw new Error(`Failed to delete board (${response.status}).`);
      }

      const departmentId = boardToDelete.departmentId;
      setBoardToDelete(null);
      await loadBoards(departmentId);
    } catch (error) {
      if (boardToDelete) {
        setDeleteBoardError("Unable to delete the board. Please try again.");
      }
      console.error("Failed to delete board:", error);
    } finally {
      setDeletingBoard(false);
    }
  }

  function requestDeleteTask(task) {
    setSelectedTask(null);
    setTaskToDelete(task);
  }

  async function confirmDeleteTask() {
    if (!taskToDelete) {
      return;
    }

    setDeletingTask(true);

    try {
      const response = await apiFetch(`${API_BASE_URL}/api/tasks/${taskToDelete.id}`, {
        method: "DELETE",
      });

      if (!response.ok) {
        if (response.status === 403) {
          setPermissionMessage("You do not have permission to delete this task.");
        }
        throw new Error("Failed to delete task");
      }

      setTaskToDelete(null);
      setStaffRefreshKey((currentKey) => currentKey + 1);

      if (selectedBoardId !== null) {
        await loadBoard(selectedBoardId);
      }
    } catch (error) {
      console.error("Failed to delete task:", error);
    } finally {
      setDeletingTask(false);
    }
  }

  if (window.location.pathname === "/activate") return <ActivationPage />;

  if (loading) {
    return <div className="app-loading">Loading...</div>;
  }

  if (!isAuthenticated) {
    return <LoginPage />;
  }

  return (
    <AppShell
      user={user}
      activeView={activeView}
      onNavigate={(view) => {
        if (view === "dashboard") setStaffRefreshKey((currentKey) => currentKey + 1);
        if (view === "report") { setSelectedReportUserId(null); setSelectedReportDate(null); }
        setActiveView(view);
        const path=view==='completed'?'/completed':view==='requests'?'/requests':view==='desktop-notifications'?'/settings/desktop-notifications':view==='ppc-planning'?'/ppc/planning':view==='ppc-arrivals'?'/ppc/raw-material-arrivals':view==='users-admin'?'/admin/users':view==='data-management'?'/admin/settings/data-management':view==='client-access'?'/admin/settings/client-access':view==='report'?'/reports/monthly':'/';
        window.history.pushState({},'',path);
      }}
      onNotificationNavigate={(notification) => {
        if (notification.requestId) {
          setActiveView("requests");
          window.history.pushState({}, "", `/requests/${notification.requestId}`);
        } else if (notification.rawMaterialArrivalId) {
          setActiveView("ppc-arrivals");
          window.history.pushState({}, "", `/ppc/raw-material-arrivals?arrivalId=${notification.rawMaterialArrivalId}`);
        } else if (isAdmin) {
          if (notification.boardId) {
            setSelectedBoardId(notification.boardId);
            setActiveView("project");
          } else {
            setActiveView("dashboard");
          }
        } else if (notification.type === "TASK_REVIEW_SUBMITTED") {
          setActiveView("reviews");
        } else if (notification.type === "TASK_REVIEW_RETURNED" || notification.type === "TASK_APPROVED") {
          setActiveView("personal");
        } else if (notification.type === "DAILY_REPORT_SUBMITTED") {
          setSelectedReportUserId(null);
          setSelectedReportDate(null);
          setActiveView("report");
        } else if (notification.boardId) {
          setSelectedBoardId(notification.boardId);
          setActiveView("project");
        } else {
          setActiveView("personal");
        }
      }}
      onLogout={logout}
    >
    <div className="app">
      {activeView === "completed" ? (
        <CompletedTasksPage />
      ) : activeView === "requests" ? (
        <InterdepartmentRequestsPage user={user} />
      ) : activeView === "desktop-notifications" ? (
        <DesktopNotificationsPage />
      ) : activeView === "users-admin" && isAdmin ? (
        <UserManagementPage departments={departments} onDepartmentCreated={handleDepartmentCreated} />
      ) : activeView === "data-management" && isAdmin ? (
        <DataManagementPage />
      ) : activeView === "client-access" && isAdmin ? (
        <ClientAccessPage />
      ) : activeView === "ppc-planning" ? (
        <PpcPlanningPage user={user} departments={departments} />
      ) : activeView === "ppc-arrivals" && (isAdmin || user?.departmentName?.toUpperCase() === "PPC") ? (
        <RawMaterialArrivalsPage />
      ) : activeView === "reviews" ? (
        <ReviewQueuePage onRefresh={() => setStaffRefreshKey((currentKey) => currentKey + 1)} />
      ) : activeView === "history" ? (
        <HistoryPage key={`${user?.userId}-${user?.role}`} user={user} users={users} departments={departments} />
      ) : activeView === "report" ? (
        <MonthlyReportsPage user={user} departments={departments} />
      ) : activeView === "legacy-report" ? (
        user?.role === "STAFF" || selectedReportUserId ? (
          <DailyReportPage user={user} selectedUserId={selectedReportUserId || user.userId} selectedDate={selectedReportDate} onBack={() => { setSelectedReportUserId(null); setSelectedReportDate(null); setActiveView(user.role === "STAFF" ? "dashboard" : "report"); }} onViewSnapshot={(id, date) => { setSelectedReportUserId(id); setSelectedReportDate(date); setActiveView("history"); }} />
        ) : (
          <TeamDailyReportsPage user={user} departments={departments} onOpenReport={(id, date) => { setSelectedReportUserId(id); setSelectedReportDate(date); }} onOpenOwnReport={() => { setSelectedReportUserId(user.userId); setSelectedReportDate(null); }} />
        )
      ) : activeView === "dashboard" ? (
        user?.role === "STAFF" ? (
          <StaffDashboard user={user} refreshKey={staffRefreshKey} onOpenKanban={() => setActiveView("personal")} onOpenReport={() => { setSelectedReportUserId(user.userId); setSelectedReportDate(null); setActiveView("report"); }} onOpenPlanning={() => setActiveView("ppc-planning")} onOpenRawMaterials={() => setActiveView("ppc-arrivals")} />
        ) : user?.role === "ADMIN" ? (
          <AdminDashboard
            user={user}
            departments={departments}
            refreshKey={staffRefreshKey}
            onViewDepartment={(departmentId) => {
              setSelectedDepartmentId(departmentId);
              setSelectedStaffId("");
              setActiveView("staff");
            }}
            onViewProject={(board) => {
              setSelectedDepartmentId(board.departmentId);
              setSelectedBoardId(board.id);
              setActiveView("project");
            }}
          />
        ) : (
          <ManagerDashboard
            user={user}
            refreshKey={staffRefreshKey}
            onOpenKanban={() => setActiveView("personal")}
            onOpenReport={() => { setSelectedReportUserId(null); setSelectedReportDate(null); setActiveView("report"); }}
            onOpenReviews={() => setActiveView("reviews")}
            onOpenPlanning={() => setActiveView("ppc-planning")}
            onOpenRawMaterials={() => setActiveView("ppc-arrivals")}
            onViewKanban={(staffId) => {
              setSelectedStaffId(String(staffId));
              setActiveView("staff");
            }}
            onViewProject={(board) => {
              setSelectedDepartmentId(board.departmentId);
              setSelectedBoardId(board.id);
              setActiveView("project");
            }}
          />
        )
      ) : activeView === "personal" ? (
        <PersonalKanban user={user} users={users} departments={departments} />
      ) : activeView === "staff" ? (
        <>
          <div className="board-toolbar staff-selector-toolbar">
            <div className="toolbar-field">
              <label htmlFor="staff-select">Staff member</label>
              <select
                id="staff-select"
                value={selectedStaffId}
                onChange={(event) => setSelectedStaffId(event.target.value)}
              >
                <option value="">Select staff member</option>
                {users.filter((candidate) => candidate.role === "STAFF").map((candidate) => (
                  <option key={candidate.id} value={candidate.id}>
                    {candidate.name || candidate.email}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <StaffKanban
            staffUser={selectedStaff}
            currentUser={user}
            refreshKey={staffRefreshKey}
            onTaskSelected={setSelectedTask}
            onTaskChanged={() => setStaffRefreshKey((currentKey) => currentKey + 1)}
            onReassignTask={setTaskToReassign}
          />
        </>
      ) : (
        <>
      <h1>{selectedBoardId === "general" ? "General Tasks" : boards.find((board) => board.id === selectedBoardId)?.name || "Company Kanban"}</h1>

      <div className="board-toolbar">
        <div className="toolbar-field">
          <label htmlFor={isAdmin ? "department-select" : undefined}>
            Department
          </label>

          {isAdmin ? (
            <select
              id="department-select"
              value={selectedDepartmentId ?? ""}
              onChange={(event) =>
                setSelectedDepartmentId(Number(event.target.value))
              }
            >
              {departments.map((department) => (
                <option key={department.id} value={department.id}>
                  {department.name}
                </option>
              ))}
            </select>
          ) : (
            <div className="department-display" aria-label="Department">
              {user?.departmentName}
            </div>
          )}
        </div>

        <div className="toolbar-field">
          <label htmlFor="board-select">Board</label>

          <select
            id="board-select"
            value={selectedBoardId ?? ""}
            onChange={(event) => setSelectedBoardId(event.target.value === "general" ? "general" : Number(event.target.value))}
            disabled={boards.length === 0 && !canShowGeneralBoard}
            >
            {canShowGeneralBoard && <option value="general">General Tasks</option>}
            {boards.map((board) => (
              <option key={board.id} value={board.id}>
                {board.name}
              </option>
            ))}
          </select>
        </div>

        {canManageBoards && (
          <>
            <button
              type="button"
              className="create-board-button"
              onClick={() => setBoardToEdit(
                boards.find((board) => board.id === selectedBoardId) ?? null
              )}
              disabled={selectedBoardId === null || selectedBoardId === "general"}
            >
              Edit Board
            </button>
            <button
              type="button"
              className="create-board-button"
              onClick={() =>
                requestDeleteBoard(
                  boards.find((board) => board.id === selectedBoardId) ?? null
                )
              }
              disabled={selectedBoardId === null || selectedBoardId === "general"}
            >
              Delete Board
            </button>
            <button
              type="button"
              className="create-board-button"
              onClick={() => setShowCreateBoard(true)}
            >
              + Create Board
            </button>
          </>
        )}
        <button type="button" className="create-board-button" onClick={() => openCreateTaskModal(null)}>
          + Add Task
        </button>
      </div>

      {selectedDepartmentId !== null && boards.length === 0 && !canShowGeneralBoard && (
        <div className="empty-state">
          <p>No boards found for this department.</p>
        </div>
      )}

      {selectedBoardId === "general" && (
        <div className="kanban-board general-task-board">
          {[
            ["DRAFT", "To Do"],
            ["DOING", "In Progress"],
            ["REVIEW", "Review"],
            ["DONE", "Done"],
          ].map(([status, label]) => (
              <div className="kanban-column" key={status} data-general-status={status}>
              <div className="column-header">
                <h2>{label}</h2>
              </div>
              <div className="task-list">
                {generalTasks.filter((task) => task.status === status).map((task) => (
                  <div key={task.id} className="task-wrapper">
                    {dropIndicator?.status === status && dropIndicator?.taskId === task.id && dropIndicator?.position === "before" && <div className="drop-indicator" />}
                    <div
                      className={`task-card priority-card-${String(task.priority || "MEDIUM").toLowerCase()} ${draggedTask?.id === task.id ? "task-card--dragging" : ""}`}
                      data-general-task-id={task.id}
                      data-general-status={status}
                      onPointerDown={(event) => handlePointerDown(event, task)}
                    ><TaskCardContent task={task} /><RddTaskActions task={task} user={user} onChanged={() => loadGeneralTasks(selectedDepartmentId)} /></div>
                    {dropIndicator?.status === status && dropIndicator?.taskId === task.id && dropIndicator?.position === "after" && <div className="drop-indicator" />}
                  </div>
                ))}
                {dropIndicator?.status === status && dropIndicator?.taskId === null && <div className="drop-indicator" />}
              </div>
            </div>
          ))}
        </div>
      )}

      {selectedBoardId !== null && selectedBoardId !== "general" && (
        <div className="kanban-board">
          {columns.map((column) => (
            <div
              className="kanban-column"
              key={column.id}
              data-column-id={column.id}
            >
              <div className="column-header">
                <h2>{column.name}</h2>
              </div>

              <div className="task-list">
                {(tasksByColumn[column.id] || []).map((task) => (
                  <div key={task.id} className="task-wrapper">
                    {dropIndicator?.columnId === column.id &&
                      dropIndicator?.taskId === task.id &&
                      dropIndicator?.position === "before" && (
                        <div className="drop-indicator" />
                      )}

                    <div
                      className={`task-card priority-card-${String(task.priority || "MEDIUM").toLowerCase()} ${task.departmentName?.toUpperCase() === "RDD" ? "task-card--rdd" : ""} ${
                        draggedTask?.id === task.id ? "task-card--dragging" : ""
                      } ${
                        task.assigneeId === user?.userId ? "" : "task-card--read-only"
                      }`}
                      data-column-id={column.id}
                      data-task-id={task.id}
                      onPointerDown={(event) => handlePointerDown(event, task)}
                    >
                      <TaskCardContent task={task} showBoardName={false} />
                      <RddTaskActions task={task} user={user} onChanged={() => loadBoard(selectedBoardId)} />
                    </div>

                    {dropIndicator?.columnId === column.id &&
                      dropIndicator?.taskId === task.id &&
                      dropIndicator?.position === "after" && (
                        <div className="drop-indicator" />
                      )}
                  </div>
                ))}

                {dropIndicator?.columnId === column.id &&
                  dropIndicator?.taskId === null && (
                    <div className="drop-indicator" />
                  )}
              </div>
            </div>
          ))}
        </div>
      )}

      {dragPreview && (
        <div
          className="drag-preview"
          style={{
            left: `${dragPreview.x - dragPreview.offsetX}px`,
            top: `${dragPreview.y - dragPreview.offsetY}px`,
            width: `${dragPreview.width}px`,
          }}
        >
          <div className="task-card drag-preview-card">
            <TaskCardContent task={dragPreview.task} showBoardName={false} />
          </div>
        </div>
      )}
        </>
      )}

      {showCreateTaskModal && (
        <CreateTaskModal
          isOpen
          column={selectedColumn}
          board={boards.find((board) => board.id === selectedBoardId) ?? null}
          boards={boards}
          departmentId={selectedDepartmentId}
          canChooseGeneral={canShowGeneralBoard}
          users={users}
          user={user}
          onClose={closeCreateTaskModal}
          onCreated={async (createdTask) => {
            setStaffRefreshKey((currentKey) => currentKey + 1);
            if (createdTask?.boardId) {
              setSelectedBoardId(createdTask.boardId);
              await loadBoard(createdTask.boardId);
            } else if (createdTask?.generalTask) {
              setSelectedBoardId("general");
              await loadGeneralTasks(selectedDepartmentId);
            }
          }}
        />
      )}

      <CreateBoardModal
        isOpen={showCreateBoard}
        user={user}
        departments={departments}
        onClose={() => setShowCreateBoard(false)}
        onCreated={handleBoardCreated}
      />

      <EditBoardModal
        key={boardToEdit?.id ?? "closed"}
        board={boardToEdit}
        onClose={() => setBoardToEdit(null)}
        onUpdated={handleBoardUpdated}
      />

      <EditTaskModal
        key={selectedTask?.id ?? "closed"}
        task={selectedTask}
        user={user}
        users={users}
        canDelete={canDeleteTask}
        onClose={() => setSelectedTask(null)}
        onTaskUpdated={async () => {
          if (selectedBoardId !== null) {
            await loadBoard(selectedBoardId);
          }
          setStaffRefreshKey((currentKey) => currentKey + 1);
        }}
        onDelete={requestDeleteTask}
      />

      <ReassignTaskModal
        task={taskToReassign}
        users={users}
        onClose={() => setTaskToReassign(null)}
        onReassigned={async (updatedTask) => {
          setTaskToReassign(null);
          setStaffRefreshKey((currentKey) => currentKey + 1);
          if (selectedBoardId !== null) {
            await loadBoard(selectedBoardId);
          }
          return updatedTask;
        }}
      />

      <ConfirmDeleteModal
        task={taskToDelete}
        deleting={deletingTask}
        onCancel={() => setTaskToDelete(null)}
        onConfirm={confirmDeleteTask}
      />

      <ConfirmDeleteBoardModal
        board={boardToDelete}
        deleting={deletingBoard}
        error={deleteBoardError}
        onCancel={cancelDeleteBoard}
        onConfirm={confirmDeleteBoard}
      />
    </div>
    </AppShell>
  );
}

export default App;
