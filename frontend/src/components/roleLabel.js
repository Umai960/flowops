// Roles are stored as STAFF / MANAGER / ADMIN; the company calls department managers "HOD".
const ROLE_LABELS = { MANAGER: "HOD", STAFF: "Staff", ADMIN: "Admin" };

export function roleLabel(role) {
  return ROLE_LABELS[role] ?? role ?? "";
}
