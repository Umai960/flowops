// Pure helpers for the Gantt page. Dates are "YYYY-MM-DD" strings; day numbers
// count whole days since 1970-01-01 so the maths never touches time zones.

const DAY_MS = 86400000;
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export const GANTT_SCALES = {
  day: { label: "Day", dayWidth: 36 },
  week: { label: "Week", dayWidth: 14 },
  month: { label: "Month", dayWidth: 4 },
};

// Task progress follows the board column the task is in.
export const STATUS_PROGRESS = { DRAFT: 0, DOING: 50, REVIEW: 80, DONE: 100 };
export const STATUS_LABELS = { DRAFT: "To Do", DOING: "In Progress", REVIEW: "Review", DONE: "Done" };

// Bar colours for the same colour keys as task cards; "" is the default teal.
export const GANTT_COLORS = {
  "": "#2f8d9d", blue: "#2563eb", green: "#16a34a", orange: "#ea7a0c", red: "#dc2626",
  purple: "#7c3aed", yellow: "#ca8a04", pink: "#db2777", gray: "#6b7280",
};

export function toDayNumber(value) {
  const [year, month, day] = String(value).slice(0, 10).split("-").map(Number);
  return Math.round(Date.UTC(year, month - 1, day) / DAY_MS);
}

export function fromDayNumber(dayNumber) {
  return new Date(dayNumber * DAY_MS).toISOString().slice(0, 10);
}

export function todayDayNumber(now = new Date()) {
  return Math.round(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / DAY_MS);
}

/**
 * Where a task's bar sits. Tasks without a start date start on the day they
 * were created; tasks without a due date are one day long.
 */
export function taskSpan(task) {
  const created = task.createdAt ? toDayNumber(task.createdAt) : null;
  const due = task.dueDate ? toDayNumber(task.dueDate) : null;
  let start = task.startDate ? toDayNumber(task.startDate) : created ?? due;
  if (start == null) return null;
  let end = due ?? start;
  if (!task.startDate && end < start) start = end;
  if (end < start) end = start;
  return { start, end, estimatedStart: !task.startDate };
}

export function taskProgress(task) {
  return STATUS_PROGRESS[task.status] ?? 0;
}

/** Share of a project's tasks that are Done, as a whole percentage. */
export function projectProgress(tasks) {
  if (!tasks.length) return 0;
  return Math.round(tasks.filter((task) => task.status === "DONE").length * 100 / tasks.length);
}

export function projectSpan(tasks) {
  const spans = tasks.map(taskSpan).filter(Boolean);
  if (!spans.length) return null;
  return { start: Math.min(...spans.map((span) => span.start)), end: Math.max(...spans.map((span) => span.end)) };
}

/** First and last day shown, with some room either side and always including today. */
export function chartRange(projects, scale, today) {
  const spans = projects.flatMap((project) => project.tasks.map(taskSpan)).filter(Boolean);
  const pad = scale === "month" ? 30 : scale === "week" ? 14 : 5;
  const start = Math.min(today, ...spans.map((span) => span.start)) - pad;
  const end = Math.max(today, ...spans.map((span) => span.end)) + pad * 2;
  if (scale === "day") return { start, end };
  // Week starts on Monday (1970-01-01 was a Thursday) and month on the 1st, so header cells line up.
  const weekStart = start - ((start + 3) % 7);
  if (scale === "week") return { start: weekStart, end };
  const first = new Date(start * DAY_MS);
  return { start: Math.round(Date.UTC(first.getUTCFullYear(), first.getUTCMonth(), 1) / DAY_MS), end };
}

/** Header cells: a top row (months or years) and a bottom row (days, weeks or months). */
export function headerCells(range, scale) {
  const top = [];
  const bottom = [];
  for (let day = range.start; day <= range.end; day += 1) {
    const date = new Date(day * DAY_MS);
    const dayOfMonth = date.getUTCDate();
    const month = date.getUTCMonth();
    const year = date.getUTCFullYear();
    const topLabel = scale === "month" ? String(year) : `${MONTHS[month]} ${year}`;
    const topKey = scale === "month" ? year : year * 12 + month;
    if (!top.length || top.at(-1).key !== topKey) top.push({ key: topKey, label: topLabel, start: day, days: 0 });
    top.at(-1).days += 1;

    if (scale === "day") {
      const weekday = date.getUTCDay();
      bottom.push({ key: day, label: String(dayOfMonth), start: day, days: 1, weekend: weekday === 0 || weekday === 6 });
    } else if (scale === "week") {
      if ((day - range.start) % 7 === 0) bottom.push({ key: day, label: `${dayOfMonth} ${MONTHS[month]}`, start: day, days: 0 });
      bottom.at(-1).days += 1;
    } else {
      const key = year * 12 + month;
      if (!bottom.length || bottom.at(-1).key !== key) bottom.push({ key, label: MONTHS[month], start: day, days: 0 });
      bottom.at(-1).days += 1;
    }
  }
  return { top, bottom };
}

/** New dates after dragging a bar by `delta` days. `mode` is move, start or end. */
export function draggedSpan(span, mode, delta) {
  if (mode === "move") return { start: span.start + delta, end: span.end + delta };
  if (mode === "start") return { start: Math.min(span.start + delta, span.end), end: span.end };
  return { start: span.start, end: Math.max(span.end + delta, span.start) };
}

export function formatDay(dayNumber) {
  const date = new Date(dayNumber * DAY_MS);
  return `${date.getUTCDate()} ${MONTHS[date.getUTCMonth()]} ${date.getUTCFullYear()}`;
}

/**
 * A one-page PDF holding a single JPEG image, so the chart can be saved as PDF
 * without an extra library. Width and height are the image size in pixels.
 */
export function jpegToPdf(jpegBytes, width, height) {
  const maxPoints = 14000;
  const scale = Math.min(0.75, maxPoints / width, maxPoints / height);
  const pageWidth = Math.max(1, Math.round(width * scale));
  const pageHeight = Math.max(1, Math.round(height * scale));
  const content = `q ${pageWidth} 0 0 ${pageHeight} 0 0 cm /Im0 Do Q`;
  const encoder = new TextEncoder();
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth} ${pageHeight}] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>`,
    null,
    `<< /Length ${content.length} >>\nstream\n${content}\nendstream`,
  ];
  const parts = [encoder.encode("%PDF-1.4\n")];
  const offsets = [];
  let length = parts[0].length;
  objects.forEach((body, index) => {
    offsets.push(length);
    const chunk = body == null
      ? [encoder.encode(`${index + 1} 0 obj\n<< /Type /XObject /Subtype /Image /Width ${width} /Height ${height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.length} >>\nstream\n`), jpegBytes, encoder.encode("\nendstream\nendobj\n")]
      : [encoder.encode(`${index + 1} 0 obj\n${body}\nendobj\n`)];
    chunk.forEach((piece) => { parts.push(piece); length += piece.length; });
  });
  const xref = `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.map((offset) => `${String(offset).padStart(10, "0")} 00000 n \n`).join("")}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${length}\n%%EOF\n`;
  parts.push(encoder.encode(xref));
  const pdf = new Uint8Array(length + parts.at(-1).length);
  let offset = 0;
  parts.forEach((piece) => { pdf.set(piece, offset); offset += piece.length; });
  return pdf;
}
