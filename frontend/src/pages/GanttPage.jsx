import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import {
  chartRange, draggedSpan, formatDay, fromDayNumber, GANTT_COLORS, GANTT_SCALES, headerCells, jpegToPdf,
  projectProgress, projectSpan, STATUS_LABELS, taskProgress, taskSpan, todayDayNumber,
} from "./ganttUtils.js";

const LABEL_WIDTH = 260;
const HEADER_HEIGHT = 52;
const PROJECT_ROW = 36;
const TASK_ROW = 30;
const BAR_HEIGHT = 16;
const FONT = "Segoe UI, Arial, sans-serif";
const EXPORT_TITLE_HEIGHT = 44;

function shorten(text, max) {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

function escapeXml(text) {
  return text.replace(/[<>&"]/g, (character) => ({ "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;" })[character]);
}

function download(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// Project tasks drawn on a timeline, grouped by project. Like Planning, users see
// their own department; administrators can pick a department or see them all.
export default function GanttPage({ user, departments = [] }) {
  const isAdmin = user?.role === "ADMIN";
  const today = useMemo(() => todayDayNumber(), []);
  const [departmentId, setDepartmentId] = useState(isAdmin ? "" : user?.departmentId ?? "");
  const [scale, setScale] = useState("week");
  const [projects, setProjects] = useState([]);
  const [collapsed, setCollapsed] = useState(() => new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [drag, setDrag] = useState(null);
  const [exporting, setExporting] = useState(false);
  const scrollRef = useRef(null);
  const bodyRef = useRef(null);
  const leftRef = useRef(null);
  const rightRef = useRef(null);

  const departmentName = isAdmin
    ? departments.find((department) => department.id === Number(departmentId))?.name || "All departments"
    : user?.departmentName || "Department";

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const query = isAdmin && departmentId ? `?departmentId=${departmentId}` : "";
      const response = await apiFetch(`/api/gantt${query}`);
      if (!response.ok) throw new Error(`Unable to load projects (${response.status}).`);
      setProjects(await response.json());
    } catch (loadError) {
      setProjects([]); setError(loadError.message);
    } finally {
      setLoading(false);
    }
  }, [departmentId, isAdmin]);

  useEffect(() => { load(); }, [load]);

  const dayWidth = GANTT_SCALES[scale].dayWidth;
  const range = useMemo(() => chartRange(projects, scale, today), [projects, scale, today]);
  const header = useMemo(() => headerCells(range, scale), [range, scale]);
  const timelineWidth = (range.end - range.start + 1) * dayWidth;
  const x = (day) => (day - range.start) * dayWidth;

  const rows = useMemo(() => {
    const result = [];
    let y = HEADER_HEIGHT;
    projects.forEach((project) => {
      result.push({ type: "project", project, y, height: PROJECT_ROW });
      y += PROJECT_ROW;
      if (collapsed.has(project.id)) return;
      project.tasks.forEach((task) => {
        result.push({ type: "task", task, project, y, height: TASK_ROW });
        y += TASK_ROW;
      });
    });
    return { list: result, height: y + 8 };
  }, [projects, collapsed]);

  const scrollToToday = useCallback(() => {
    const target = (today - range.start) * dayWidth;
    const scroller = scrollRef.current;
    if (scroller) scroller.scrollLeft = Math.max(0, target - scroller.clientWidth / 3);
    // On phones the whole chart, labels included, scrolls sideways instead.
    const body = bodyRef.current;
    if (body && body.scrollWidth > body.clientWidth) body.scrollLeft = Math.max(0, LABEL_WIDTH + target - body.clientWidth / 2);
  }, [range.start, dayWidth, today]);

  // Jump to today when the chart first loads and when the scale or department changes, but not after a drag.
  const pendingScroll = useRef(true);
  useEffect(() => { pendingScroll.current = true; }, [scale, departmentId]);
  useEffect(() => {
    if (loading || !pendingScroll.current) return;
    pendingScroll.current = false;
    scrollToToday();
  });

  function toggleProject(projectId) {
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(projectId)) next.delete(projectId); else next.add(projectId);
      return next;
    });
  }

  function startDrag(event, task, mode) {
    if (event.button !== 0) return;
    event.preventDefault();
    rightRef.current?.setPointerCapture(event.pointerId);
    setDrag({ task, mode, originX: event.clientX, span: taskSpan(task), delta: 0 });
  }

  function moveDrag(event) {
    if (!drag) return;
    const delta = Math.round((event.clientX - drag.originX) / dayWidth);
    if (delta !== drag.delta) setDrag({ ...drag, delta });
  }

  async function endDrag() {
    if (!drag) return;
    const { task, mode, span, delta } = drag;
    setDrag(null);
    if (delta === 0) return;
    const next = draggedSpan(span, mode, delta);
    const dates = { startDate: fromDayNumber(next.start), dueDate: fromDayNumber(next.end) };
    const replaceTask = (updated) => setProjects((current) => current.map((project) => ({
      ...project, tasks: project.tasks.map((candidate) => candidate.id === updated.id ? updated : candidate),
    })));
    replaceTask({ ...task, ...dates });
    try {
      const response = await apiFetch(`/api/tasks/${task.id}/dates`, { method: "PUT", body: JSON.stringify(dates) });
      if (!response.ok) throw new Error(response.status === 403 ? "You do not have permission to change this task." : `Unable to save the new dates (${response.status}).`);
      replaceTask(await response.json());
      setError("");
    } catch (saveError) {
      replaceTask(task);
      setError(saveError.message);
    }
  }

  async function exportChart(kind) {
    if (!leftRef.current || !rightRef.current) return;
    setExporting(true); setError("");
    try {
      const serializer = new XMLSerializer();
      const inner = (svg) => Array.from(svg.childNodes).map((node) => serializer.serializeToString(node)).join("");
      const width = LABEL_WIDTH + timelineWidth;
      const height = rows.height + EXPORT_TITLE_HEIGHT;
      const title = `${departmentName} · Gantt chart · ${formatDay(today)}`;
      const svgText = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">`
        + `<rect width="${width}" height="${height}" fill="#ffffff"/>`
        + `<text x="16" y="28" font-family="${FONT}" font-size="17" font-weight="700" fill="#0f172a">${escapeXml(title)}</text>`
        + `<g transform="translate(0 ${EXPORT_TITLE_HEIGHT})" font-family="${FONT}">${inner(leftRef.current)}</g>`
        + `<g transform="translate(${LABEL_WIDTH} ${EXPORT_TITLE_HEIGHT})" font-family="${FONT}">${inner(rightRef.current)}</g></svg>`;
      const svgUrl = URL.createObjectURL(new Blob([svgText], { type: "image/svg+xml" }));
      const image = new Image();
      image.src = svgUrl;
      await image.decode();
      URL.revokeObjectURL(svgUrl);

      const ratio = Math.min(2, 16000 / width, 16000 / height);
      const canvas = document.createElement("canvas");
      canvas.width = Math.round(width * ratio);
      canvas.height = Math.round(height * ratio);
      const context = canvas.getContext("2d");
      context.fillStyle = "#ffffff";
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(image, 0, 0, canvas.width, canvas.height);

      const filename = `gantt-${departmentName.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-${fromDayNumber(today)}`;
      const blob = await new Promise((resolve) => canvas.toBlob(resolve, kind === "pdf" ? "image/jpeg" : "image/png", 0.92));
      if (!blob) throw new Error("The chart is too large to export.");
      if (kind === "pdf") {
        const pdf = jpegToPdf(new Uint8Array(await blob.arrayBuffer()), canvas.width, canvas.height);
        download(new Blob([pdf], { type: "application/pdf" }), `${filename}.pdf`);
      } else {
        download(blob, `${filename}.png`);
      }
    } catch (exportError) {
      setError(exportError.message || "Unable to export the chart.");
    } finally {
      setExporting(false);
    }
  }

  const taskCount = projects.reduce((total, project) => total + project.tasks.length, 0);

  return <section className="gantt-page">
    <div className="admin-page-heading">
      <div>
        <p className="eyebrow">{departmentName} / Gantt</p>
        <h1>Gantt Chart</h1>
        <p>Project tasks on a timeline. Drag a bar to move it, or drag its ends to change the start or due date.</p>
        {isAdmin && <label className="planning-department-picker">Department <select value={departmentId} onChange={(event) => setDepartmentId(event.target.value)}><option value="">All departments</option>{departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}</select></label>}
      </div>
      <div className="gantt-export-buttons">
        <button type="button" className="secondary-button" onClick={() => exportChart("png")} disabled={exporting || loading}>Export image</button>
        <button type="button" className="secondary-button" onClick={() => exportChart("pdf")} disabled={exporting || loading}>Export PDF</button>
      </div>
    </div>

    <div className="gantt-toolbar">
      <div className="gantt-scale" role="group" aria-label="Timeline scale">
        {Object.entries(GANTT_SCALES).map(([key, option]) => <button key={key} type="button" className={scale === key ? "is-active" : ""} onClick={() => setScale(key)}>{option.label}</button>)}
      </div>
      <button type="button" className="secondary-button" onClick={scrollToToday}>Today</button>
      <span className="gantt-legend"><i className="gantt-legend-estimated" /> Dashed outline: no start date yet, so the bar starts on the day the task was created.</span>
    </div>

    {error && <div className="admin-message error">{error}</div>}

    <div className="gantt-card">
      {loading ? <div className="gantt-empty">Loading projects…</div> : !projects.length ? <div className="gantt-empty">No projects yet.</div> : <div ref={bodyRef} className="gantt-body">
        <svg ref={leftRef} className="gantt-labels" width={LABEL_WIDTH} height={rows.height} fontFamily={FONT}>
          <rect width={LABEL_WIDTH} height={rows.height} fill="#ffffff" />
          <rect width={LABEL_WIDTH} height={HEADER_HEIGHT} fill="#f8fafc" />
          <text x="14" y="32" fontSize="12" fontWeight="700" fill="#475569">{projects.length} projects · {taskCount} tasks</text>
          <line x1="0" x2={LABEL_WIDTH} y1={HEADER_HEIGHT - 0.5} y2={HEADER_HEIGHT - 0.5} stroke="#cbd5e1" />
          {rows.list.map((row) => row.type === "project" ? <g key={`p${row.project.id}`} className="gantt-project-label" onClick={() => toggleProject(row.project.id)}>
            <rect y={row.y} width={LABEL_WIDTH} height={row.height} fill="#eef3fb" />
            <text x="12" y={row.y + 23} fontSize="13" fontWeight="700" fill="#0f172a">{collapsed.has(row.project.id) ? "▸" : "▾"} {shorten(row.project.name, isAdmin && !departmentId ? 18 : 24)}{isAdmin && !departmentId ? ` (${row.project.departmentName})` : ""}<title>{row.project.name}</title></text>
            <text x={LABEL_WIDTH - 12} y={row.y + 23} fontSize="12" fontWeight="700" fill="#2f6feb" textAnchor="end">{projectProgress(row.project.tasks)}%</text>
            <line x1="0" x2={LABEL_WIDTH} y1={row.y + row.height - 0.5} y2={row.y + row.height - 0.5} stroke="#e2e8f0" />
          </g> : <g key={`t${row.task.id}`}>
            <text x="30" y={row.y + 19} fontSize="12" fill="#1e293b">{shorten(row.task.title, 22)}<title>{row.task.title}</title></text>
            <text x={LABEL_WIDTH - 12} y={row.y + 19} fontSize="11" fill="#64748b" textAnchor="end">{shorten(row.task.assigneeName || "Unassigned", 12)}</text>
            <line x1="0" x2={LABEL_WIDTH} y1={row.y + row.height - 0.5} y2={row.y + row.height - 0.5} stroke="#f1f5f9" />
          </g>)}
          <line x1={LABEL_WIDTH - 0.5} x2={LABEL_WIDTH - 0.5} y1="0" y2={rows.height} stroke="#cbd5e1" />
        </svg>

        <div ref={scrollRef} className="gantt-scroll">
          <svg ref={rightRef} className={`gantt-timeline${drag ? " is-dragging" : ""}`} width={timelineWidth} height={rows.height} fontFamily={FONT}
            onPointerMove={moveDrag} onPointerUp={endDrag} onPointerCancel={() => setDrag(null)}>
            <rect width={timelineWidth} height={rows.height} fill="#ffffff" />
            {header.bottom.filter((cell) => cell.weekend).map((cell) => <rect key={`w${cell.key}`} x={x(cell.start)} y={HEADER_HEIGHT} width={dayWidth} height={rows.height - HEADER_HEIGHT} fill="#f8fafc" />)}
            {rows.list.filter((row) => row.type === "project").map((row) => <rect key={`pb${row.project.id}`} y={row.y} width={timelineWidth} height={row.height} fill="#eef3fb" opacity="0.6" />)}
            <rect width={timelineWidth} height={HEADER_HEIGHT} fill="#f8fafc" />
            {header.top.map((cell) => <g key={`h${cell.key}`}>
              <text x={x(cell.start) + 6} y="18" fontSize="12" fontWeight="700" fill="#334155">{cell.days * dayWidth > 40 ? cell.label : ""}</text>
              <line x1={x(cell.start) + 0.5} x2={x(cell.start) + 0.5} y1="0" y2={HEADER_HEIGHT / 2} stroke="#cbd5e1" />
            </g>)}
            {header.bottom.map((cell) => <g key={`b${cell.key}`}>
              <text x={x(cell.start) + (cell.days * dayWidth) / 2} y="42" fontSize="11" fill="#64748b" textAnchor="middle">{cell.days * dayWidth >= 18 ? cell.label : ""}</text>
              <line x1={x(cell.start) + 0.5} x2={x(cell.start) + 0.5} y1={HEADER_HEIGHT / 2} y2={rows.height} stroke="#eef2f7" />
            </g>)}
            <line x1="0" x2={timelineWidth} y1={HEADER_HEIGHT - 0.5} y2={HEADER_HEIGHT - 0.5} stroke="#cbd5e1" />

            {rows.list.map((row) => {
              if (row.type === "project") {
                const span = projectSpan(row.project.tasks);
                if (!span) return <text key={`ps${row.project.id}`} x={x(today) + 8} y={row.y + 22} fontSize="11" fill="#94a3b8">No tasks yet</text>;
                const barX = x(span.start);
                const barWidth = (span.end - span.start + 1) * dayWidth;
                const progress = projectProgress(row.project.tasks);
                return <g key={`ps${row.project.id}`}>
                  <rect x={barX} y={row.y + 13} width={barWidth} height="10" rx="3" fill="#cbd5e1" />
                  <rect x={barX} y={row.y + 13} width={(barWidth * progress) / 100} height="10" rx="3" fill="#334155" />
                  <text x={barX + barWidth + 6} y={row.y + 22} fontSize="11" fontWeight="700" fill="#334155">{progress}%</text>
                </g>;
              }
              const { task } = row;
              const baseSpan = taskSpan(task);
              if (!baseSpan) return null;
              const isDragged = drag?.task.id === task.id;
              const span = isDragged ? draggedSpan(drag.span, drag.mode, drag.delta) : baseSpan;
              const barX = x(span.start);
              const barWidth = (span.end - span.start + 1) * dayWidth;
              const barY = row.y + (TASK_ROW - BAR_HEIGHT) / 2;
              const color = GANTT_COLORS[task.color || ""] || GANTT_COLORS[""];
              const progress = taskProgress(task);
              const handle = Math.min(8, barWidth / 3);
              return <g key={`tb${task.id}`}>
                <title>{`${task.title}\n${formatDay(span.start)} – ${formatDay(span.end)}\n${STATUS_LABELS[task.status] || task.status} · ${progress}%\n${task.assigneeName || "Unassigned"}`}</title>
                <rect x={barX} y={barY} width={barWidth} height={BAR_HEIGHT} rx="4" fill={color} fillOpacity="0.28"
                  stroke={color} strokeWidth="1" strokeDasharray={baseSpan.estimatedStart && !isDragged ? "4 3" : undefined} />
                {progress > 0 && <rect x={barX} y={barY} width={(barWidth * progress) / 100} height={BAR_HEIGHT} rx="4" fill={color} />}
                <rect className="gantt-bar-move" x={barX} y={barY} width={barWidth} height={BAR_HEIGHT} fill="transparent" onPointerDown={(event) => startDrag(event, task, "move")} />
                <rect className="gantt-bar-resize" x={barX} y={barY} width={handle} height={BAR_HEIGHT} fill="transparent" onPointerDown={(event) => startDrag(event, task, "start")} />
                <rect className="gantt-bar-resize" x={barX + barWidth - handle} y={barY} width={handle} height={BAR_HEIGHT} fill="transparent" onPointerDown={(event) => startDrag(event, task, "end")} />
                <text x={barX + barWidth + 6} y={barY + 12} fontSize="11" fill="#334155">{progress}%{isDragged ? `  ${formatDay(span.start)} – ${formatDay(span.end)}` : ""}</text>
              </g>;
            })}

            <line x1={x(today) + dayWidth / 2} x2={x(today) + dayWidth / 2} y1={HEADER_HEIGHT} y2={rows.height} stroke="#dc2626" strokeWidth="1.5" strokeDasharray="5 4" />
          </svg>
        </div>
      </div>}
    </div>
  </section>;
}
