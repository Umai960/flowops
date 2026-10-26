import test from "node:test";
import assert from "node:assert/strict";
import { getNavigationItems } from "../src/components/navigationModel.js";

test("PPC staff navigation follows operational priority order", () => {
  assert.deepEqual(getNavigationItems({ role: "STAFF", departmentName: "PPC" }).map((item) => item[0]), ["dashboard", "personal", "project", "ppc-arrivals", "ppc-planning", "requests", "completed", "report", "desktop-notifications"]);
});
test("PPC manager navigation puts team and reviews after project work", () => {
  assert.deepEqual(getNavigationItems({ role: "MANAGER", departmentName: "PPC" }).map((item) => item[0]), ["dashboard", "personal", "project", "ppc-arrivals", "ppc-planning", "requests", "completed", "staff", "reviews", "report", "desktop-notifications"]);
});
test("non-PPC staff gets their own Planning calendar but not other PPC modules", () => {
  const ids = getNavigationItems({ role: "STAFF", departmentName: "RDD" }).map((item) => item[0]);
  assert.equal(ids.includes("ppc-planning"), true);
  assert.equal(ids.includes("ppc-arrivals"), false);
});
test("managers and admins of any department see Planning", () => {
  assert.equal(getNavigationItems({ role: "MANAGER", departmentName: "PROD" }).some((item) => item[0] === "ppc-planning"), true);
  assert.equal(getNavigationItems({ role: "ADMIN", departmentName: "ADMIN" }).some((item) => item[0] === "ppc-planning"), true);
});
