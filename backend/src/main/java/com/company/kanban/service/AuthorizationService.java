package com.company.kanban.service;

import com.company.kanban.entity.Board;
import com.company.kanban.entity.Department;
import com.company.kanban.entity.KanbanColumn;
import com.company.kanban.entity.Role;
import com.company.kanban.entity.Task;
import com.company.kanban.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
public class AuthorizationService {

    public boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public void requirePpcPlanningAccess(User user) {
        if (isAdmin(user)) return;
        if (user == null || user.getDepartment() == null
                || !"PPC".equalsIgnoreCase(user.getDepartment().getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "PPC Planning is available only to PPC users and administrators");
        }
    }

    public void requirePpcRawMaterialArrivalAccess(User user) {
        if (isAdmin(user)) return;
        if (user == null || user.getDepartment() == null
                || !"PPC".equalsIgnoreCase(user.getDepartment().getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Raw material arrivals are available only to PPC users and administrators");
        }
    }

    public boolean canAccessDepartment(User user, Long departmentId) {
        return isAdmin(user)
                || user != null
                && user.getDepartment() != null
                && Objects.equals(user.getDepartment().getId(), departmentId);
    }

    public void requireDepartmentAccess(User user, Long departmentId) {
        if (!canAccessDepartment(user, departmentId)) {
            throw forbidden();
        }
    }

    public void requireBoardManagementAccess(User user, Long departmentId) {
        if (isAdmin(user)) {
            return;
        }

        if (user == null || user.getRole() != Role.MANAGER
                || !canAccessDepartment(user, departmentId)) {
            throw forbidden();
        }
    }

    public void requireBoardAccess(User user, Board board) {
        requireDepartmentAccess(user, board.getDepartment().getId());
    }

    public void requireColumnAccess(User user, KanbanColumn column) {
        requireBoardAccess(user, column.getBoard());
    }

    public void requireTaskAccess(User user, Task task) {
        Department department = task.getDepartment();
        if (department == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task has no department");
        }
        requireDepartmentAccess(user, department.getId());
    }

    public void requireTaskOwnerMove(User user, Task task) {
        // Moving a task is a department-board operation.  Editing/reassigning
        // ownership remains separately protected, but board members must be
        // able to keep the board organized even when they are not the assignee.
        requireTaskAccess(user, task);
    }

    public void requirePersonalTaskAccess(User user, Task task) {
        if (user == null || task.getAssignee() == null
                || !Objects.equals(user.getId(), task.getAssignee().getId())) {
            throw forbidden();
        }
    }

    public void requirePersonalStatusMove(User user, Task task, com.company.kanban.entity.TaskStatus targetStatus) {
        // Status buttons and drag/drop are both department-board operations.
        // Personal Kanban still only displays the signed-in user's tasks.
        requireTaskAccess(user, task);

        if (user.getRole() == Role.STAFF
                && !task.isGeneralTask()
                && targetStatus == com.company.kanban.entity.TaskStatus.DONE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Staff tasks must be approved by a HOD before completion"
            );
        }
    }

    public void requireStaffViewerAccess(User currentUser, User staffUser) {
        if (isAdmin(currentUser)) {
            return;
        }

        // The owner may always read their own data, regardless of role.
        if (currentUser != null && staffUser != null
                && Objects.equals(currentUser.getId(), staffUser.getId())) {
            return;
        }

        if (currentUser == null || currentUser.getRole() != Role.MANAGER
                || currentUser.getDepartment() == null
                || staffUser == null || staffUser.getDepartment() == null
                || !Objects.equals(currentUser.getDepartment().getId(),
                staffUser.getDepartment().getId())) {
            throw forbidden();
        }
    }

    public void requireWorkloadDashboardAccess(User currentUser) {
        if (currentUser == null
                || (currentUser.getRole() != Role.ADMIN
                && currentUser.getRole() != Role.MANAGER)) {
            throw forbidden();
        }
    }

    public void requireReviewActionAccess(User currentUser, Task task, com.company.kanban.dto.ReviewAction action) {
        if (isAdmin(currentUser)) {
            return;
        }

        if (currentUser == null) {
            throw forbidden();
        }

        requireTaskAccess(currentUser, task);
        if (currentUser.getRole() == Role.MANAGER) return;
        if (action == com.company.kanban.dto.ReviewAction.RETURN
                && task.getDepartment() != null
                && "RDD".equalsIgnoreCase(task.getDepartment().getName())
                && task.getAssignee() != null
                && Objects.equals(currentUser.getId(), task.getAssignee().getId())) return;
        throw forbidden();
    }

    public void requireAssignableUser(User currentUser, User assignee) {
        if (isAdmin(currentUser)) {
            return;
        }

        if (currentUser == null
                || currentUser.getDepartment() == null
                || assignee == null
                || assignee.getDepartment() == null
                || !currentUser.getDepartment().getId()
                .equals(assignee.getDepartment().getId())) {

            throw forbidden();
        }

        if (currentUser.getRole() == Role.STAFF && assignee.getRole() != Role.STAFF) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Staff can only assign tasks to staff in their department"
            );
        }
    }

    public void requireTaskReassignmentAccess(User currentUser, Task task, User assignee) {
        if (currentUser == null
                || (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER)) {
            throw forbidden();
        }

        requireTaskAccess(currentUser, task);
        requireAssignableUser(currentUser, assignee);

        if (assignee.getRole() != Role.STAFF) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tasks can only be reassigned to staff"
            );
        }

        requireAssigneeMatchesTaskDepartment(assignee, task.getDepartment());
    }

    public void requireGeneralTaskCreation(User currentUser, Department department) {
        if (!isAdmin(currentUser)) {
            if (currentUser == null || currentUser.getDepartment() == null
                    || !Objects.equals(currentUser.getDepartment().getId(), department.getId())) {
                throw forbidden();
            }
        }
    }

    public void requireAssigneeMatchesTaskDepartment(
            User assignee,
            Department taskDepartment) {

        if (assignee == null) {
            return;
        }

        if (taskDepartment == null
                || assignee.getDepartment() == null
                || !assignee.getDepartment().getId()
                .equals(taskDepartment.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Assignee must belong to the task's department"
            );
        }
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this department's data"
        );
    }
}
