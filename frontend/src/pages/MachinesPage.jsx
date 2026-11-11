import { useCallback, useEffect, useMemo, useState } from "react";
import { apiFetch } from "../api/apiFetch";
import { authorizedFetch } from "../api/chatApi";

const STATUSES = ["RUNNING", "BREAKDOWN", "UNDER_MAINTENANCE", "IDLE"];
const EMPTY = { code: "", name: "", location: "", model: "", serialNumber: "", status: "RUNNING", notes: "", active: true };
const TEMPLATE = "Code,Name,Location,Model,Serial No,Status,Notes\r\n";
const label = (value) => value?.replaceAll("_", " ").toLowerCase().replace(/^./, (c) => c.toUpperCase());

export function formatDowntime(minutes) {
  if (minutes == null) return "";
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  const mins = minutes % 60;
  if (days) return `${days}d ${hours}h`;
  if (hours) return `${hours}h ${mins}m`;
  return `${mins}m`;
}

async function errorText(response, fallback) {
  try {
    const body = await response.json();
    return body.message || body.error || fallback;
  } catch {
    return fallback;
  }
}

function MachineModal({ machine, onClose, onSaved }) {
  const [form, setForm] = useState(machine ? { ...EMPTY, ...machine } : EMPTY);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const set = (key) => (event) => setForm((current) => ({ ...current, [key]: event.target.type === "checkbox" ? event.target.checked : event.target.value }));

  async function save(event) {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      const payload = { ...form, code: form.code.trim(), name: form.name.trim() };
      const response = await apiFetch(machine ? `/api/machines/${machine.id}` : "/api/machines", { method: machine ? "PUT" : "POST", body: JSON.stringify(payload) });
      if (!response.ok) throw new Error(await errorText(response, `Save failed (${response.status})`));
      onSaved();
    } catch (x) {
      setError(x.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal-card machine-modal" role="dialog" aria-modal="true" aria-labelledby="machine-modal-title">
        <div className="modal-header">
          <div><p className="eyebrow">Maintenance / Machines</p><h2 id="machine-modal-title">{machine ? "Edit machine" : "Add machine"}</h2></div>
          <button className="modal-close" type="button" onClick={onClose} aria-label="Close">×</button>
        </div>
        {error && <div className="admin-message error">{error}</div>}
        <form className="admin-form" onSubmit={save}>
          <label>Code<input value={form.code} onChange={set("code")} maxLength={60} required autoFocus /></label>
          <label>Name<input value={form.name} onChange={set("name")} maxLength={160} required /></label>
          <label>Location / Line<input value={form.location || ""} onChange={set("location")} maxLength={120} /></label>
          <label>Model<input value={form.model || ""} onChange={set("model")} maxLength={120} /></label>
          <label>Serial No<input value={form.serialNumber || ""} onChange={set("serialNumber")} maxLength={120} /></label>
          <label>Status<select value={form.status} onChange={set("status")}>{STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}</select></label>
          <label className="form-wide">Notes<textarea value={form.notes || ""} onChange={set("notes")} maxLength={2000} /></label>
          {machine && <label className="machine-active-toggle"><input type="checkbox" checked={form.active} onChange={set("active")} /> Active (untick for machines no longer in use)</label>}
          <div className="modal-actions">
            <button type="button" className="secondary-button" onClick={onClose}>Cancel</button>
            <button className="primary-button" disabled={saving}>{saving ? "Saving…" : machine ? "Save changes" : "Add machine"}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

function ReportModal({ machine, onClose, onSaved }) {
  const [problem, setProblem] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function save(event) {
    event.preventDefault();
    if (!problem.trim()) { setError("Describe the problem."); return; }
    setSaving(true);
    const response = await apiFetch(`/api/machines/${machine.id}/breakdowns`, { method: "POST", body: JSON.stringify({ problem: problem.trim() }) });
    setSaving(false);
    if (!response.ok) { setError(await errorText(response, "Unable to report breakdown")); return; }
    onSaved();
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal-card machine-modal" role="dialog" aria-modal="true" aria-labelledby="report-modal-title">
        <div className="modal-header">
          <div><p className="eyebrow">{machine.code} · {machine.name}</p><h2 id="report-modal-title">Report breakdown</h2></div>
          <button className="modal-close" type="button" onClick={onClose} aria-label="Close">×</button>
        </div>
        {error && <div className="admin-message error">{error}</div>}
        <form className="admin-form" onSubmit={save}>
          <label className="form-wide">Problem<textarea value={problem} onChange={(e) => setProblem(e.target.value)} maxLength={2000} required autoFocus placeholder="What is wrong with the machine?" /></label>
          <div className="modal-actions">
            <button type="button" className="secondary-button" onClick={onClose}>Cancel</button>
            <button className="danger-button" disabled={saving}>{saving ? "Saving…" : "Report breakdown"}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

function HistoryModal({ machine, onClose }) {
  const [items, setItems] = useState(null);
  useEffect(() => {
    apiFetch(`/api/machines/${machine.id}/breakdowns`).then((r) => (r.ok ? r.json() : [])).then(setItems).catch(() => setItems([]));
  }, [machine.id]);
  const total = (items || []).reduce((sum, b) => sum + b.downtimeMinutes, 0);

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal-card machine-modal machine-history-modal" role="dialog" aria-modal="true" aria-labelledby="history-modal-title">
        <div className="modal-header">
          <div><p className="eyebrow">{machine.code} · {machine.name}</p><h2 id="history-modal-title">Breakdown history</h2></div>
          <button className="modal-close" type="button" onClick={onClose} aria-label="Close">×</button>
        </div>
        {items === null ? <p>Loading…</p> : !items.length ? <p>No breakdowns recorded.</p> : (
          <>
            <p className="machine-history-summary">{items.length} breakdowns · total downtime {formatDowntime(total)}</p>
            <ul className="machine-history-list">
              {items.map((b) => (
                <li key={b.id}>
                  <div><strong>{new Date(b.reportedAt).toLocaleString()}</strong><span className={`status-badge machine-breakdown-${b.status.toLowerCase()}`}>{label(b.status)}</span></div>
                  <p>{b.problem}</p>
                  {b.actionTaken && <p className="machine-action-taken">Action: {b.actionTaken}</p>}
                  <small>Reported by {b.reportedByName}{b.completedByName ? ` · completed by ${b.completedByName}` : ""} · downtime {formatDowntime(b.downtimeMinutes)}</small>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </div>
  );
}

function BreakdownCard({ breakdown, onChanged, onError }) {
  const [action, setAction] = useState(breakdown.actionTaken || "");
  const [busy, setBusy] = useState(false);

  async function update(status) {
    setBusy(true);
    const response = await apiFetch(`/api/machines/breakdowns/${breakdown.id}`, { method: "PUT", body: JSON.stringify({ status, actionTaken: action.trim() || null }) });
    setBusy(false);
    if (!response.ok) { onError(await errorText(response, "Unable to update breakdown")); return; }
    onChanged();
  }

  const open = breakdown.status !== "COMPLETED";
  return (
    <article className="request-card machine-breakdown-card">
      <div className="request-card-top">
        <div><div className="request-route"><span>{breakdown.machineCode}</span></div><h3>{breakdown.machineName}</h3></div>
        <span className={`status-badge machine-breakdown-${breakdown.status.toLowerCase()}`}>{label(breakdown.status)}</span>
      </div>
      <p className="request-description">{breakdown.problem}</p>
      <div className="request-meta">
        <span>Reported by {breakdown.reportedByName}</span>
        <span>{new Date(breakdown.reportedAt).toLocaleString()}</span>
        <span>Downtime {formatDowntime(breakdown.downtimeMinutes)}</span>
        {breakdown.completedByName && <span>Completed by {breakdown.completedByName}</span>}
      </div>
      {open ? (
        <>
          <label className="machine-action-field">Action taken<textarea value={action} onChange={(e) => setAction(e.target.value)} maxLength={2000} placeholder="What was done to fix it?" disabled={busy} /></label>
          <div className="request-actions">
            {breakdown.status === "OPEN" && <button className="secondary-button" disabled={busy} onClick={() => update("IN_PROGRESS")}>Start repair</button>}
            {breakdown.status === "IN_PROGRESS" && <button className="secondary-button" disabled={busy} onClick={() => update("IN_PROGRESS")}>Save note</button>}
            <button className="primary-button" disabled={busy} onClick={() => update("COMPLETED")}>Mark repaired</button>
          </div>
        </>
      ) : breakdown.actionTaken && <p className="machine-action-taken">Action: {breakdown.actionTaken}</p>}
    </article>
  );
}

export default function MachinesPage({ user }) {
  const canManage = user?.role === "ADMIN" || user?.role === "MANAGER";
  const [tab, setTab] = useState("machines");
  const [machines, setMachines] = useState([]);
  const [breakdowns, setBreakdowns] = useState([]);
  const [breakdownView, setBreakdownView] = useState("open");
  const [search, setSearch] = useState("");
  const [showInactive, setShowInactive] = useState(false);
  const [modal, setModal] = useState(null);
  const [message, setMessage] = useState(null);
  const [importing, setImporting] = useState(false);

  const loadMachines = useCallback(async () => {
    const response = await apiFetch("/api/machines");
    if (response.ok) setMachines(await response.json());
    else setMessage({ type: "error", text: await errorText(response, "Unable to load machines") });
  }, []);
  const loadBreakdowns = useCallback(async () => {
    const response = await apiFetch(`/api/machines/breakdowns?view=${breakdownView}`);
    if (response.ok) setBreakdowns(await response.json());
  }, [breakdownView]);
  const reload = useCallback(() => { loadMachines(); loadBreakdowns(); }, [loadMachines, loadBreakdowns]);

  useEffect(() => { loadMachines(); }, [loadMachines]);
  useEffect(() => { loadBreakdowns(); }, [loadBreakdowns]);

  const visible = useMemo(() => {
    const query = search.trim().toLowerCase();
    return machines.filter((m) => (showInactive || m.active) && (!query || [m.code, m.name, m.location, m.model, m.serialNumber].some((v) => v?.toLowerCase().includes(query))));
  }, [machines, search, showInactive]);
  const counts = useMemo(() => STATUSES.reduce((acc, s) => ({ ...acc, [s]: machines.filter((m) => m.active && m.status === s).length }), {}), [machines]);

  function downloadTemplate() {
    const url = URL.createObjectURL(new Blob(["﻿" + TEMPLATE], { type: "text/csv" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "machines-template.csv";
    link.click();
    URL.revokeObjectURL(url);
  }

  async function importFile(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setImporting(true);
    setMessage(null);
    const body = new FormData();
    body.append("file", file);
    const response = await authorizedFetch("/api/machines/import", { method: "POST", body });
    setImporting(false);
    if (!response.ok) { setMessage({ type: "error", text: await errorText(response, "Import failed") }); return; }
    const result = await response.json();
    setMessage({ type: result.errors.length ? "error" : "success", text: `Imported: ${result.created} new, ${result.updated} updated.${result.errors.length ? ` Skipped ${result.errors.length}: ${result.errors.slice(0, 5).join("; ")}${result.errors.length > 5 ? "…" : ""}` : ""}` });
    loadMachines();
  }

  const done = () => { setModal(null); reload(); };

  return (
    <section className="machines-page">
      <div className="admin-page-heading">
        <div><p className="eyebrow">Maintenance</p><h1>Machines</h1><p>Machine list, breakdown reports and repair records.</p></div>
        {canManage && tab === "machines" && (
          <div className="machines-heading-actions">
            <button className="secondary-button" type="button" onClick={downloadTemplate}>Download template</button>
            <label className={`secondary-button machines-import${importing ? " is-busy" : ""}`}>{importing ? "Importing…" : "Import CSV"}<input type="file" accept=".csv,text/csv" onChange={importFile} disabled={importing} hidden /></label>
            <button className="primary-button" type="button" onClick={() => setModal({ type: "machine" })}>+ Add machine</button>
          </div>
        )}
      </div>
      {message && <div className={`admin-message ${message.type}`}>{message.text}</div>}
      <div className="machine-summary">
        {STATUSES.map((s) => <div key={s} className={`machine-summary-card machine-status-${s.toLowerCase()}`}><span>{label(s)}</span><strong>{counts[s] || 0}</strong></div>)}
      </div>
      <div className="request-view-switcher machines-tabs" role="tablist" aria-label="Machines view">
        <button type="button" className={tab === "machines" ? "is-active" : ""} onClick={() => setTab("machines")}>Machines</button>
        <button type="button" className={tab === "breakdowns" ? "is-active" : ""} onClick={() => setTab("breakdowns")}>Breakdowns</button>
      </div>

      {tab === "machines" ? (
        <>
          <div className="machines-filters">
            <input type="search" placeholder="Search code, name, location…" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search machines" />
            <label><input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} /> Show inactive</label>
          </div>
          <div className="admin-table-wrap">
            <table className="admin-table machines-table">
              <thead><tr><th>Code</th><th>Name</th><th>Location</th><th>Model</th><th>Serial No</th><th>Status</th><th /></tr></thead>
              <tbody>
                {!visible.length && <tr><td colSpan={7} className="machines-empty">{machines.length ? "No machines match." : canManage ? "No machines yet. Add one or import a CSV file." : "No machines yet."}</td></tr>}
                {visible.map((m) => (
                  <tr key={m.id} className={m.active ? "" : "machine-inactive"}>
                    <td><strong>{m.code}</strong></td>
                    <td>{m.name}{!m.active && <small> (inactive)</small>}</td>
                    <td>{m.location}</td>
                    <td>{m.model}</td>
                    <td>{m.serialNumber}</td>
                    <td><span className={`status-pill machine-status-${m.status.toLowerCase()}`}>{label(m.status)}</span></td>
                    <td className="machines-row-actions">
                      {m.active && <button type="button" className="danger-outline-button" onClick={() => setModal({ type: "report", machine: m })}>Report breakdown</button>}
                      <button type="button" className="secondary-button" onClick={() => setModal({ type: "history", machine: m })}>History</button>
                      {canManage && <button type="button" className="secondary-button" onClick={() => setModal({ type: "machine", machine: m })}>Edit</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : (
        <>
          <div className="request-view-switcher machines-subtabs" role="tablist" aria-label="Breakdown view">
            <button type="button" className={breakdownView === "open" ? "is-active" : ""} onClick={() => setBreakdownView("open")}>Open</button>
            <button type="button" className={breakdownView === "history" ? "is-active" : ""} onClick={() => setBreakdownView("history")}>Completed</button>
          </div>
          <div className="request-list">
            {!breakdowns.length && <div className="request-empty"><h3>{breakdownView === "open" ? "No open breakdowns" : "No completed breakdowns"}</h3><p>{breakdownView === "open" ? "All machines are running." : "Repaired breakdowns will appear here."}</p></div>}
            {breakdowns.map((b) => <BreakdownCard key={`${b.id}-${b.status}`} breakdown={b} onChanged={reload} onError={(text) => setMessage({ type: "error", text })} />)}
          </div>
        </>
      )}

      {modal?.type === "machine" && <MachineModal machine={modal.machine} onClose={() => setModal(null)} onSaved={done} />}
      {modal?.type === "report" && <ReportModal machine={modal.machine} onClose={() => setModal(null)} onSaved={() => { done(); setTab("breakdowns"); setBreakdownView("open"); }} />}
      {modal?.type === "history" && <HistoryModal machine={modal.machine} onClose={() => setModal(null)} />}
    </section>
  );
}
