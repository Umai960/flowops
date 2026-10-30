import { PLANNING_COLORS } from "../pages/ppcPlanningUtils.js";

// Same colour choices as Planning items; "" keeps the default white card.
export function taskColorClass(task) {
  return task?.color ? ` task-color-${task.color}` : "";
}

function TaskColorPicker({ name, value, onChange }) {
  const selected = value || "";
  return (
    <fieldset className="planning-color-field">
      <legend>Colour</legend>
      <div className="planning-color-options">
        {PLANNING_COLORS.map(([key, label]) => (
          <label key={key || "default"} className={`planning-color-swatch${key ? ` planning-color-${key}` : " task-color-default-swatch"}${selected === key ? " is-selected" : ""}`} title={label}>
            <input type="radio" name={name} value={key} checked={selected === key} onChange={() => onChange(key)} />
            <span className="sr-only">{label}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

export default TaskColorPicker;
