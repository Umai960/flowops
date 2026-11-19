// Display helpers for the Machines page.

export function addMonths(iso, months) {
  const [y, m, d] = iso.split("-").map(Number);
  const target = new Date(Date.UTC(y, m - 1 + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d, lastDay));
  return target.toISOString().slice(0, 10);
}

export function expiryText(part) {
  if (part.expiryStatus === "REPLACED") return `Replaced ${part.replacedOn}`;
  if (part.daysLeft == null) return "No expiry";
  if (part.daysLeft < 0) return `Expired ${-part.daysLeft} day${part.daysLeft === -1 ? "" : "s"} ago`;
  if (part.daysLeft === 0) return "Expires today";
  return `${part.daysLeft} day${part.daysLeft === 1 ? "" : "s"} left`;
}

export function formatDowntime(minutes) {
  if (minutes == null) return "";
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  const mins = minutes % 60;
  if (days) return `${days}d ${hours}h`;
  if (hours) return `${hours}h ${mins}m`;
  return `${mins}m`;
}
