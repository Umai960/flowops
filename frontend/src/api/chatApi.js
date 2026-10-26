// Chat inside tasks and planning items. Uploads and the live stream need the token
// without the JSON Content-Type that apiFetch adds, so they use authorizedFetch.

export const CHAT_MAX_FILE_BYTES = 10 * 1024 * 1024;
export const CHAT_ACCEPTED_FILES = ".jpg,.jpeg,.png,.gif,.webp,.bmp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.csv,.txt,.zip";

export function chatPath(subjectType, subjectId) {
  return `/api/chat/${String(subjectType).toLowerCase()}/${subjectId}`;
}

export function authorizedFetch(url, options = {}) {
  const token = localStorage.getItem("token");
  return fetch(url, { ...options, headers: { ...options.headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) } });
}

export async function sendChatMessage(subjectType, subjectId, body, file) {
  const form = new FormData();
  if (body?.trim()) form.append("body", body);
  if (file) form.append("file", file);
  const response = await authorizedFetch(`${chatPath(subjectType, subjectId)}/messages`, { method: "POST", body: form });
  if (!response.ok) throw new Error(await errorMessage(response, "Message could not be sent."));
  return response.json();
}

export async function fetchAttachmentUrl(messageId) {
  const response = await authorizedFetch(`/api/chat/messages/${messageId}/attachment`);
  if (!response.ok) throw new Error("File could not be opened.");
  return URL.createObjectURL(await response.blob());
}

export async function errorMessage(response, fallback) {
  if (response.status === 413) return "File is larger than 10 MB.";
  try {
    const data = await response.json();
    return data?.message || data?.detail || fallback;
  } catch {
    return fallback;
  }
}

export function validateChatFile(file) {
  if (!file) return "";
  const extension = file.name.includes(".") ? file.name.split(".").pop().toLowerCase() : "";
  if (!CHAT_ACCEPTED_FILES.split(",").includes(`.${extension}`)) return "This file type is not allowed. Use images, PDF, Word, Excel, PowerPoint, CSV, TXT or ZIP.";
  if (file.size > CHAT_MAX_FILE_BYTES) return "File is larger than 10 MB.";
  return "";
}

export function formatFileSize(bytes) {
  if (!Number.isFinite(bytes)) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Adds or replaces messages by id and keeps them in sending order. */
export function mergeMessages(current, incoming) {
  const byId = new Map(current.map((message) => [message.id, message]));
  incoming.forEach((message) => byId.set(message.id, message));
  return [...byId.values()].sort((a, b) => a.id - b.id);
}

export function isSameSubject(event, subjectType, subjectId) {
  return String(event?.subjectType).toLowerCase() === String(subjectType).toLowerCase() && Number(event?.subjectId) === Number(subjectId);
}

/** Splits a text/event-stream into (eventName, data) pairs; comment lines such as pings are ignored. */
export function createSseParser(onEvent) {
  let buffer = "";
  return (chunk) => {
    buffer += chunk;
    let match;
    while ((match = /\r?\n\r?\n/.exec(buffer))) {
      const block = buffer.slice(0, match.index);
      buffer = buffer.slice(match.index + match[0].length);
      let name = "message";
      const data = [];
      block.split(/\r?\n/).forEach((line) => {
        if (line.startsWith("event:")) name = line.slice(6).trim();
        else if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
      });
      if (data.length) onEvent(name, data.join("\n"));
    }
  };
}

export function formatChatTime(value, now = new Date()) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const time = date.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
  if (date.toDateString() === now.toDateString()) return time;
  return `${date.toLocaleDateString("en-GB", { day: "numeric", month: "short" })}, ${time}`;
}
