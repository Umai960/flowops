export function formatDateInput(value) {
  return value ? String(value).slice(0, 10) : "";
}

export function dateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function addDays(dateValue, amount) {
  const date = new Date(`${formatDateInput(dateValue)}T00:00:00`);
  date.setDate(date.getDate() + amount);
  return dateKey(date);
}

// FlowOps endDate is inclusive; FullCalendar all-day end dates are exclusive.
// Colour keys accepted by the server; "" keeps the default teal.
export const PLANNING_COLORS = [
  ["", "Default"], ["blue", "Blue"], ["green", "Green"], ["orange", "Orange"], ["red", "Red"],
  ["purple", "Purple"], ["yellow", "Yellow"], ["pink", "Pink"], ["gray", "Gray"],
];

export function planningColorClass(item) {
  return item?.color ? ` planning-color-${item.color}` : "";
}

export function planningItemToEvent(item) {
  return { id: String(item.id), title: item.title, start: formatDateInput(item.startDate), end: addDays(item.endDate, 1), allDay: true, extendedProps: { item } };
}

export function eventDatesToPlanningDates(event) {
  const startDate = event.startStr.slice(0, 10);
  const exclusiveEnd = event.endStr || addDays(startDate, 1);
  return { startDate, endDate: addDays(exclusiveEnd, -1) };
}

export function itemsForDate(items, date) {
  const key = date instanceof Date ? dateKey(date) : formatDateInput(date);
  return items.filter((item) => formatDateInput(item.startDate) <= key && formatDateInput(item.endDate) >= key);
}
