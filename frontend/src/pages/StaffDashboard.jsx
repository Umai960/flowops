import { useEffect, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import { activeTasks, countStatus, dueLabel, formatDueDate, getJson, malaysiaToday, timeGreeting } from "./dashboardUtils";

function StaffDashboard({ user, refreshKey, onOpenKanban, onOpenReport, onOpenPlanning, onOpenRawMaterials }) {
  if (user?.departmentName?.toUpperCase() === "PPC") return <PpcStaffDashboard user={user} refreshKey={refreshKey} onOpenKanban={onOpenKanban} onOpenPlanning={onOpenPlanning} onOpenRawMaterials={onOpenRawMaterials} />;
  return <LegacyStaffDashboard user={user} refreshKey={refreshKey} onOpenKanban={onOpenKanban} onOpenReport={onOpenReport} />;
}

function LegacyStaffDashboard({ user, refreshKey, onOpenKanban, onOpenReport }) {
  const [tasks, setTasks] = useState([]);
  const [snapshot, setSnapshot] = useState(null);
  const [state, setState] = useState("loading");

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setState("loading");
      try {
        const [myTasks, dates] = await Promise.all([
          getJson("/api/tasks/my", apiFetch),
          getJson("/api/history/dates", apiFetch).catch(() => []),
        ]);
        let startOfDay = null;
        const today = malaysiaToday();
        if (dates.some((date) => date.date === today && date.hasStartOfDay)) {
          startOfDay = await getJson(`/api/history/${today}?type=START_OF_DAY`, apiFetch).catch(() => null);
        }
        if (!cancelled) { setTasks(myTasks); setSnapshot(startOfDay); setState("ready"); }
      } catch (error) {
        console.error(error);
        if (!cancelled) setState("error");
      }
    }
    load();
    return () => { cancelled = true; };
  }, [refreshKey]);

  if (state === "loading") return <section className="dashboard-page"><p className="dashboard-status">Loading your dashboard...</p></section>;
  if (state === "error") return <section className="dashboard-page"><div className="dashboard-error">Unable to load dashboard.</div></section>;

  const active = activeTasks(tasks);
  const attention = active
    .filter((task) => task.status === "REVIEW" || dueLabel(task))
    .sort((a, b) => attentionRank(a) - attentionRank(b))
    .slice(0, 6);
  const current = tasks.filter((task) => task.status === "DOING").concat(tasks.filter((task) => task.status === "DRAFT")).slice(0, 5);
  const statusCount = (status) => countStatus(tasks, status);

  return <section className="dashboard-page">
    <div className="dashboard-hero"><div><h1 className="personal-greeting">{timeGreeting(user.name)}</h1></div><button className="primary-button" onClick={onOpenKanban}>Open My Kanban</button></div>
    <div className="kpi-grid">
      <Kpi label="My tasks" value={active.length} detail="To Do, In Progress and Review" />
      <Kpi label="Doing" value={statusCount("DOING")} detail="In progress now" />
      <Kpi label="Waiting for review" value={statusCount("REVIEW")} detail="Awaiting HOD action" />
      <Kpi label="Due soon / overdue" value={active.filter((task) => dueLabel(task)).length} detail="Next 3 days" tone={active.some((task) => dueLabel(task)) ? "warning" : ""} />
    </div>
    <div className="dashboard-columns">
      <DashboardPanel title="Today's Report" action={<button className="text-button" onClick={onOpenReport}>Open</button>}><ReportCard onOpen={onOpenReport} /></DashboardPanel>
      <DashboardPanel title="My current work" action={<button className="text-button" onClick={onOpenKanban}>View all</button>}>
        {current.length ? <TaskList tasks={current} /> : <Empty text="No active tasks." />}
      </DashboardPanel>
      <DashboardPanel title="Needs attention">
        {attention.length ? <AttentionList tasks={attention} /> : <Empty text="Nothing needs your attention right now." />}
      </DashboardPanel>
    </div>
    <DashboardPanel title="Today's progress">
      {snapshot ? <ProgressComparison snapshot={snapshot} current={tasks} /> : <Empty text="Start-of-day snapshot not available." />}
    </DashboardPanel>
  </section>;
}
function ReportCard({onOpen}) { const [status,setStatus]=useState("loading"); useEffect(()=>{apiFetch("/api/daily-reports/today").then(r=>r.ok?r.json():null).then(d=>setStatus(d?.report?.reportStatus||"NOT_STARTED")).catch(()=>setStatus("NOT_STARTED"));},[]); return <div className="daily-report-card"><span className={`report-status ${status.toLowerCase()}`}>{status === "NOT_STARTED" ? "Not started" : status === "DRAFT" ? "Draft" : "Submitted"}</span><p>{status === "NOT_STARTED" ? "Capture a short update for today's work." : status === "DRAFT" ? "Your draft is ready to continue." : "Your report is ready to view."}</p><button className="primary-button" onClick={onOpen}>{status === "NOT_STARTED" ? "Write Report" : status === "DRAFT" ? "Continue Report" : "View Report"}</button></div>; }

function Kpi({ label, value, detail, tone = "" }) { return <div className={`kpi-card ${tone}`}><span>{label}</span><strong>{value}</strong><small>{detail}</small></div>; }
function DashboardPanel({ title, action, children, className = "" }) { return <section className={`dashboard-panel ${className}`}><div className="panel-heading"><h2>{title}</h2>{action}</div>{children}</section>; }
function Empty({ text }) { return <p className="dashboard-empty">{text}</p>; }
function TaskList({ tasks }) { return <div className="dashboard-task-list">{tasks.map((task) => <div className="dashboard-task" key={task.id}><div><strong>{task.title}</strong><small>{task.boardName || "Personal work"} · Workload {task.workload ?? "—"}{task.dueDate ? ` · ${formatDueDate(task.dueDate)}` : ""}</small></div></div>)}</div>; }
function AttentionList({ tasks }) { return <div className="dashboard-task-list">{tasks.map((task) => { const deadline = dueLabel(task); return <div className="dashboard-task attention-task" key={task.id}><div><strong>{task.title}</strong><small>{task.boardName || "Personal work"} · Workload {task.workload ?? "—"}</small></div><div className="attention-badges">{task.status === "REVIEW" && <span className="attention-badge review">Waiting for review</span>}{deadline && <span className={`attention-badge ${deadline === "Overdue" ? "overdue" : "deadline"}`}>{deadline}</span>}</div></div>; })}</div>; }
function attentionRank(task) { if (dueLabel(task) === "Overdue") return 1; if (dueLabel(task) === "Due today") return 2; if (dueLabel(task) === "Due tomorrow") return 3; if (dueLabel(task) === "Due soon") return 4; return 5; }
function ProgressComparison({ snapshot, current }) { return <div className="progress-comparison"><div className="progress-labels"><span>Status</span><span>Start of day</span><span>Current</span></div>{["DRAFT", "DOING", "REVIEW", "DONE"].map((status) => <div className="progress-row" key={status}><strong>{status[0] + status.slice(1).toLowerCase()}</strong><b>{countStatus(snapshot, status)}</b><span>→</span><b>{countStatus(current, status)}</b></div>)}</div>; }

function PpcStaffDashboard({ user, refreshKey, onOpenKanban, onOpenPlanning, onOpenRawMaterials }) {
  const [data, setData] = useState(null);
  const [state, setState] = useState("loading");
  useEffect(() => { let cancelled = false; setState("loading"); apiFetch("/api/dashboard/ppc").then((r) => { if (!r.ok) throw new Error("dashboard"); return r.json(); }).then((value) => { if (!cancelled) { setData(value); setState("ready"); } }).catch(() => { if (!cancelled) setState("error"); }); return () => { cancelled = true; }; }, [refreshKey]);
  if (state === "loading") return <section className="dashboard-page"><p className="dashboard-status">Loading your PPC dashboard...</p></section>;
  if (state === "error") return <section className="dashboard-page"><div className="dashboard-error">Unable to load PPC dashboard.</div></section>;
  const task = data.taskSummary; const raw = data.rawMaterialAttention; const kanban = data.kanbanSummary;
  return <section className="dashboard-page">
    <div className="dashboard-hero"><div><h1 className="personal-greeting">{timeGreeting(user.name)}</h1><p className="greeting-subtitle">Your PPC priorities at a glance.</p></div><button className="primary-button" onClick={onOpenKanban}>Open My Kanban</button></div>
    <div className="kpi-grid"><Kpi label="My active tasks" value={task.active} detail="Not done" /><Kpi label="Due today" value={task.dueToday} detail="Needs attention" tone={task.dueToday ? "warning" : ""} /><Kpi label="Overdue" value={task.overdue} detail="Follow up now" tone={task.overdue ? "warning" : ""} /></div>
    <div className="dashboard-columns"><DashboardPanel title="Raw material attention" action={<button className="text-button" onClick={onOpenRawMaterials}>Open Raw Material Arrival</button>}><MetricLine label="Follow-up due" value={raw.followUpDue} /><MetricLine label="Delayed" value={raw.delayed} /><MetricLine label="Due today" value={raw.dueToday} /><MetricLine label="Arriving tomorrow" value={raw.arrivingTomorrow} />{raw.urgentItems?.length ? <div className="dashboard-task-list">{raw.urgentItems.map((item) => <div className="dashboard-task" key={item.id}><div><strong>{item.materialName}</strong><small>{item.attention}{item.delayDays ? ` · ${item.delayDays} days` : ""}</small></div></div>)}</div> : <Empty text="No materials need attention." />}</DashboardPanel>
      <DashboardPanel title="Upcoming planning" action={<button className="text-button" onClick={onOpenPlanning}>Open Planning</button>}>{data.upcomingPlanning?.length ? <div className="dashboard-task-list">{data.upcomingPlanning.map((item) => <div className="dashboard-task" key={item.id}><div><strong>{item.title}</strong><small>{formatDateRange(item.startDate, item.endDate)}{item.priority ? ` · ${item.priority}` : ""}</small></div></div>)}</div> : <Empty text="No upcoming planning items." />}</DashboardPanel></div>
    <DashboardPanel title="My Kanban summary"><div className="kpi-grid compact-kpi-grid"><Kpi label="To Do" value={kanban.toDo} /><Kpi label="In Progress" value={kanban.inProgress} /><Kpi label="Review" value={kanban.review} /></div><button className="primary-button dashboard-open-button" onClick={onOpenKanban}>View My Kanban</button></DashboardPanel>
  </section>;
}
function MetricLine({ label, value }) { return <div className="progress-row"><strong>{label}</strong><b>{value}</b></div>; }
function formatDateRange(start, end) { return start === end ? start : `${start} – ${end}`; }

export default StaffDashboard;
