import test from "node:test";
import assert from "node:assert/strict";
import { chatPath, createSseParser, formatFileSize, isSameSubject, mergeMessages, validateChatFile } from "../src/api/chatApi.js";
import { chatTargetFromLocation } from "../src/pages/chatTarget.js";

test("live stream parser returns chat events and skips pings", () => {
  const events = [];
  const parse = createSseParser((name, data) => events.push([name, data]));
  parse(":connected\n\nevent:chat\ndata:{\"kind\":\"MES");
  parse("SAGE\"}\n\n:ping\n\n");
  assert.deepEqual(events, [["chat", "{\"kind\":\"MESSAGE\"}"]]);
});

test("messages merge by id in sending order", () => {
  const merged = mergeMessages([{ id: 2, body: "b" }, { id: 1, body: "a" }], [{ id: 2, body: "edited", deleted: true }, { id: 3, body: "c" }]);
  assert.deepEqual(merged.map((message) => [message.id, message.body]), [[1, "a"], [2, "edited"], [3, "c"]]);
});

test("files are checked before upload", () => {
  assert.equal(validateChatFile({ name: "report.XLSX", size: 2048 }), "");
  assert.match(validateChatFile({ name: "setup.exe", size: 10 }), /not allowed/);
  assert.match(validateChatFile({ name: "scan.pdf", size: 11 * 1024 * 1024 }), /10 MB/);
  assert.equal(formatFileSize(1536), "2 KB");
});

test("chat links identify the task or planning item", () => {
  assert.equal(chatPath("TASK", 5), "/api/chat/task/5");
  assert.equal(isSameSubject({ subjectType: "PLANNING", subjectId: 9 }, "planning", "9"), true);
  assert.deepEqual(chatTargetFromLocation("?type=request&id=12"), { type: "request", id: 12 });
  assert.equal(chatTargetFromLocation("?type=planning&id=12"), null);
  assert.equal(chatTargetFromLocation("?type=board&id=1"), null);
});
