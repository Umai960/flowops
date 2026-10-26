import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin from "@fullcalendar/interaction";
import { apiFetch } from "../api/apiFetch";
import { dateKey, eventDatesToPlanningDates, formatDateInput, planningItemToEvent } from "./ppcPlanningUtils.js";

const EMPTY_FORM = { title: "", description: "", startDate: "", endDate: "", priority: "", status: "" };

function PlanningModal({ item, initialDate, departmentId, departmentName, onClose, onSaved, onDeleted }) {
  const [form, setForm] = useState(item ? { ...EMPTY_FORM, ...item } : { ...EMPTY_FORM, startDate: initialDate, endDate: initialDate });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const isEdit = Boolean(item);
  const update = (key) => (event) => setForm((current) => ({ ...current, [key]: event.target.value }));

  async function save(event) {
    event.preventDefault(); setError("");
    if (!form.title.trim()) { setError("Title is required."); return; }
    if (!form.startDate || !form.endDate || form.endDate < form.startDate) { setError("End date must be on or after start date."); return; }
    setSaving(true);
    try {
      const payload = { ...(!isEdit && departmentId ? { departmentId } : {}), title: form.title.trim(), description: form.description?.trim() || null, startDate: form.startDate, endDate: form.endDate, priority: form.priority?.trim() || null, status: form.status?.trim() || null };
      const response = await apiFetch(isEdit ? `/api/ppc/planning/${item.id}` : "/api/ppc/planning", { method: isEdit ? "PUT" : "POST", body: JSON.stringify(payload) });
      if (!response.ok) throw new Error((await response.text()) || `Save failed (${response.status}).`);
      onSaved(await response.json());
    } catch (saveError) { setError(saveError.message); } finally { setSaving(false); }
  }

  async function remove() {
    if (!window.confirm("Delete this planning item?")) return;
    setSaving(true); setError("");
    try { const response = await apiFetch(`/api/ppc/planning/${item.id}`, { method: "DELETE" }); if (!response.ok) throw new Error(`Delete failed (${response.status}).`); onDeleted(item.id); }
    catch (deleteError) { setError(deleteError.message); setSaving(false); }
  }

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <div className="modal-card ppc-planning-modal" role="dialog" aria-modal="true" aria-labelledby="planning-modal-title">
      <div className="modal-header"><div><p className="eyebrow">{departmentName} Planning</p><h2 id="planning-modal-title">{isEdit ? "Edit Planning Item" : "Add Planning Item"}</h2></div><button type="button" className="modal-close" onClick={onClose} aria-label="Close">×</button></div>
      {error && <div className="admin-message error">{error}</div>}
      <form className="admin-form" onSubmit={save}>
        <label>Title<input value={form.title} onChange={update("title")} maxLength={160} required autoFocus /></label>
        <label>Description<textarea value={form.description || ""} onChange={update("description")} maxLength={2000} /></label>
        <label>Start date<input type="date" value={formatDateInput(form.startDate)} onChange={update("startDate")} required /></label>
        <label>End date<input type="date" value={formatDateInput(form.endDate)} onChange={update("endDate")} required /></label>
        <div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancel</button>{isEdit && <button type="button" className="danger-button" onClick={remove} disabled={saving}>Delete</button>}<button type="submit" className="primary-button" disabled={saving}>{saving ? "Saving…" : "Save"}</button></div>
      </form>
    </div>
  </div>;
}

// Every department has its own planning calendar; administrators can switch between departments.
export default function PpcPlanningPage({ user, departments = [] }) {
  const today = useMemo(() => new Date(), []);
  const isAdmin = user?.role === "ADMIN";
  const [departmentId, setDepartmentId] = useState(user?.departmentId ?? null);
  const departmentName = (isAdmin && departments.find((department) => department.id === departmentId)?.name) || user?.departmentName || "Department";
  const departmentQuery = isAdmin && departmentId ? `&departmentId=${departmentId}` : "";
  const calendarRef = useRef(null);
  const calendarContainerRef = useRef(null);
  const [items, setItems] = useState([]); const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const [modal, setModal] = useState(null); const [calendarMonth, setCalendarMonth] = useState("");
  const loadMonth = useCallback(async (date) => {
    const year = date.getFullYear(); const month = date.getMonth() + 1;
    setCalendarMonth(`${year}-${month}`); setLoading(true); setError("");
    try { const response = await apiFetch(`/api/ppc/planning?year=${year}&month=${month}${departmentQuery}`); if (!response.ok) throw new Error(`Unable to load planning items (${response.status}).`); const data = await response.json(); if (!Array.isArray(data)) throw new Error("Planning data was not returned in the expected format."); setItems(data); }
    catch (loadError) { setItems([]); setError(loadError.message); } finally { setLoading(false); }
  }, [departmentQuery]);
  const handleDatesSet = useCallback((info) => {
    const date = info.view.currentStart;
    const nextMonth = `${date.getFullYear()}-${date.getMonth() + 1}`;
    if (nextMonth !== calendarMonth) loadMonth(date);
  }, [calendarMonth, loadMonth]);
  const updateDates = useCallback(async (eventInfo) => {
    const item = eventInfo.event.extendedProps.item; const dates = eventDatesToPlanningDates(eventInfo.event);
    try { const response = await apiFetch(`/api/ppc/planning/${item.id}`, { method: "PUT", body: JSON.stringify({ title: item.title, description: item.description, priority: item.priority, status: item.status, ...dates }) }); if (!response.ok) throw new Error((await response.text()) || `Unable to save planning dates (${response.status}).`); const saved = await response.json(); setItems((current) => current.map((entry) => entry.id === saved.id ? saved : entry)); eventInfo.event.setExtendedProp("item", saved); }
    catch (saveError) { eventInfo.revert(); setError(saveError.message); }
  }, []);
  useEffect(() => { if (!calendarMonth) loadMonth(today); }, [calendarMonth, loadMonth, today]);
  const loadedDepartmentQuery = useRef(departmentQuery);
  useEffect(() => {
    if (loadedDepartmentQuery.current === departmentQuery) return;
    loadedDepartmentQuery.current = departmentQuery;
    const api = calendarRef.current?.getApi(); if (api) loadMonth(api.view.currentStart);
  }, [departmentQuery, loadMonth]);
  useEffect(() => {
    const container = calendarContainerRef.current;
    if (!container) return undefined;

    let frame;
    const updateCalendarSize = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => calendarRef.current?.getApi().updateSize());
    };
    const observer = new ResizeObserver(updateCalendarSize);
    observer.observe(container);
    updateCalendarSize();

    return () => {
      observer.disconnect();
      cancelAnimationFrame(frame);
    };
  }, []);
  const events = useMemo(() => items.map(planningItemToEvent), [items]);
  const upsert = (saved) => { setItems((current) => current.some((entry) => entry.id === saved.id) ? current.map((entry) => entry.id === saved.id ? saved : entry) : [...current, saved]); setModal(null); };
  const removeItem = (id) => { setItems((current) => current.filter((entry) => entry.id !== id)); setModal(null); };

  return <section className="ppc-planning-page">
    <div className="admin-page-heading"><div><p className="eyebrow">{departmentName} / Planning</p><h1>Planning Calendar</h1><p>Coordinate {departmentName} planning across the month.</p>{isAdmin && departments.length > 0 && <label className="planning-department-picker">Department <select value={departmentId ?? ""} onChange={(event) => setDepartmentId(Number(event.target.value))}>{departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}</select></label>}</div><button type="button" className="primary-button" onClick={() => setModal({ initialDate: dateKey(today) })}>+ Add planning item</button></div>
    {error && <div className="admin-message error">{error}</div>}
    <div className="planning-calendar-card"><div ref={calendarContainerRef} className="planning-calendar-scroll"><FullCalendar ref={calendarRef} plugins={[dayGridPlugin, interactionPlugin]} initialView="dayGridMonth" firstDay={1} weekends editable eventDurationEditable eventStartEditable height="auto" headerToolbar={{ left: "today prev", center: "title", right: "next" }} buttonText={{ today: "Today" }} events={events} dayMaxEvents dateClick={(info) => setModal({ initialDate: info.dateStr })} eventClick={(info) => setModal({ item: info.event.extendedProps.item })} eventDrop={updateDates} eventResize={updateDates} datesSet={handleDatesSet} eventContent={(info) => { const item = info.event.extendedProps.item || {}; return <div className="flowops-calendar-event"><span className="planning-item-title">{info.event.title}</span>{(item.priority || item.status) && <small>{item.priority || item.status}</small>}</div>; }} /></div>{loading && <div className="planning-loading">Loading planning items…</div>}</div>
    {modal && <PlanningModal {...modal} departmentId={isAdmin ? departmentId : null} departmentName={departmentName} onClose={() => setModal(null)} onSaved={upsert} onDeleted={removeItem} />}
  </section>;
}
