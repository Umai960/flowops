export function chatTargetFromLocation(search = window.location.search) {
  const params = new URLSearchParams(search);
  const type = params.get("type");
  const id = Number(params.get("id"));
  return (type === "task" || type === "planning") && id > 0 ? { type, id } : null;
}
