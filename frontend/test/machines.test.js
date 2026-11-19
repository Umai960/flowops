import assert from "node:assert/strict";
import test from "node:test";

import { addMonths, expiryText, formatDowntime } from "../src/pages/machineUtils.js";
import { getNavigationItems } from "../src/components/navigationModel.js";

test("quick expiry adds calendar months and clamps to month end", () => {
  assert.equal(addMonths("2026-11-11", 6), "2027-05-11");
  assert.equal(addMonths("2026-08-31", 6), "2027-02-28");
  assert.equal(addMonths("2026-01-15", 12), "2027-01-15");
});

test("spare part expiry text", () => {
  assert.equal(expiryText({ expiryStatus: "EXPIRED", daysLeft: -3 }), "Expired 3 days ago");
  assert.equal(expiryText({ expiryStatus: "EXPIRING", daysLeft: 1 }), "1 day left");
  assert.equal(expiryText({ expiryStatus: "EXPIRING", daysLeft: 0 }), "Expires today");
  assert.equal(expiryText({ expiryStatus: "NO_EXPIRY", daysLeft: null }), "No expiry");
  assert.equal(expiryText({ expiryStatus: "REPLACED", replacedOn: "2026-11-01" }), "Replaced 2026-11-01");
});

test("downtime formatting", () => {
  assert.equal(formatDowntime(45), "45m");
  assert.equal(formatDowntime(125), "2h 5m");
  assert.equal(formatDowntime(1500), "1d 1h");
});

test("only Maintenance and admins see Machines", () => {
  const ids = (user) => getNavigationItems(user).map(([id]) => id);
  assert.ok(ids({ role: "STAFF", departmentName: "Maintenance" }).includes("machines"));
  assert.ok(ids({ role: "ADMIN", departmentName: "PPC" }).includes("machines"));
  assert.ok(!ids({ role: "MANAGER", departmentName: "PPC" }).includes("machines"));
  assert.ok(!ids({ role: "STAFF", departmentName: "PROD" }).includes("machines"));
});
