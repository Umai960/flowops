import test from "node:test";
import assert from "node:assert/strict";
import { chartRange, draggedSpan, fromDayNumber, headerCells, jpegToPdf, projectProgress, taskSpan, toDayNumber } from "../src/pages/ganttUtils.js";

const day = toDayNumber;

test("a task bar runs from its start date to its due date", () => {
  assert.deepEqual(taskSpan({ startDate: "2026-11-02", dueDate: "2026-11-06", createdAt: "2026-10-01T09:00:00" }),
    { start: day("2026-11-02"), end: day("2026-11-06"), estimatedStart: false });
});

test("tasks without a start date start on the day they were created", () => {
  assert.deepEqual(taskSpan({ dueDate: "2026-11-06", createdAt: "2026-10-30T15:20:00" }),
    { start: day("2026-10-30"), end: day("2026-11-06"), estimatedStart: true });
  // A due date before the creation day makes a one-day bar on the due date.
  assert.deepEqual(taskSpan({ dueDate: "2026-10-20", createdAt: "2026-10-30T15:20:00" }),
    { start: day("2026-10-20"), end: day("2026-10-20"), estimatedStart: true });
  // No due date: one day long.
  assert.deepEqual(taskSpan({ createdAt: "2026-10-30T15:20:00" }), { start: day("2026-10-30"), end: day("2026-10-30"), estimatedStart: true });
});

test("project progress is the share of tasks that are done", () => {
  assert.equal(projectProgress([{ status: "DONE" }, { status: "DOING" }, { status: "DRAFT" }]), 33);
  assert.equal(projectProgress([]), 0);
});

test("dragging moves the whole bar or one end, never past the other end", () => {
  const span = { start: 10, end: 14 };
  assert.deepEqual(draggedSpan(span, "move", 3), { start: 13, end: 17 });
  assert.deepEqual(draggedSpan(span, "start", -2), { start: 8, end: 14 });
  assert.deepEqual(draggedSpan(span, "start", 9), { start: 14, end: 14 });
  assert.deepEqual(draggedSpan(span, "end", -9), { start: 10, end: 10 });
});

test("day numbers round-trip across month and year ends", () => {
  assert.equal(fromDayNumber(day("2026-12-31") + 1), "2027-01-01");
  assert.equal(fromDayNumber(day("2028-02-28") + 1), "2028-02-29");
});

test("week scale starts on a Monday and month scale on the 1st", () => {
  const projects = [{ tasks: [{ startDate: "2026-11-11", dueDate: "2026-11-20" }] }];
  const today = day("2026-11-12");
  const week = chartRange(projects, "week", today);
  assert.equal(new Date(week.start * 86400000).getUTCDay(), 1);
  const month = chartRange(projects, "month", today);
  assert.equal(fromDayNumber(month.start).slice(8), "01");
  const cells = headerCells(month, "month");
  assert.equal(cells.bottom[0].label, "Oct");
  assert.equal(cells.top[0].label, "2026");
});

test("the PDF wraps the JPEG with a valid cross-reference table", () => {
  const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);
  const pdf = new TextDecoder("latin1").decode(jpegToPdf(jpeg, 800, 400));
  assert.ok(pdf.startsWith("%PDF-1.4"));
  assert.match(pdf, /\/MediaBox \[0 0 600 300\]/);
  const xrefAt = Number(pdf.match(/startxref\n(\d+)/)[1]);
  assert.equal(pdf.slice(xrefAt, xrefAt + 4), "xref");
  const offsets = [...pdf.matchAll(/(\d{10}) 00000 n/g)].map((match) => Number(match[1]));
  offsets.forEach((offset, index) => assert.ok(pdf.slice(offset).startsWith(`${index + 1} 0 obj`)));
});
