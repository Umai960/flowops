export function getNavigationItems(user) {
  const isAdmin = user?.role === "ADMIN";
  const canSeePpc = isAdmin || user?.departmentName?.toUpperCase() === "PPC";
  const canSeeTeam = user?.role === "MANAGER" || isAdmin;
  const canSeeProjects = canSeeTeam || user?.role === "STAFF";
  const canSeeRequests = isAdmin || user?.departmentName?.toUpperCase() === "RDD" || user?.departmentName?.toUpperCase() === "PPC";
  if (canSeePpc && !isAdmin) return placeCompletedSecondLast([
    ["dashboard", "Dashboard"], ["personal", "My Kanban"], ["project", "Projects"], ["ppc-arrivals", "Raw Material Arrival"], ["ppc-planning", "Planning"], ["chat", "Chat"], ["requests", "Requests"], ["completed", "Completed Tasks"],
    ...(canSeeTeam ? [["staff", "Team"]] : []), ...(user?.role === "MANAGER" ? [["reviews", "Reviews"]] : []), ["report", "Monthly Reports"], ["desktop-notifications", "Desktop Notifications"],
  ], user);
  return placeCompletedSecondLast([
    ["dashboard", "Dashboard"], ...(!isAdmin ? [["personal", "My Kanban"]] : []), ["completed", "Completed Tasks"], ...(canSeeRequests ? [["requests", "Requests"]] : []), ...(canSeeProjects ? [["project", "Projects"]] : []), ["ppc-planning", "Planning"], ["chat", "Chat"], ...(canSeeTeam ? [["staff", "Team"]] : []),
    ...(user?.role === "MANAGER" ? [["reviews", "Reviews"]] : []), ["report", "Monthly Reports"], ["desktop-notifications", "Desktop Notifications"],
    ...(isAdmin ? [["users-admin", "Users"], ["data-management", "Data Management"], ["client-access", "Client Access"]] : []),
  ], user);
}

function placeCompletedSecondLast(items, user) {
  if (user?.role !== "STAFF") return items;
  const completed = items.find((item) => item[0] === "completed");
  if (!completed) return items;
  return [...items.filter((item) => item[0] !== "completed").slice(0, -1), completed, items.at(-1)];
}
