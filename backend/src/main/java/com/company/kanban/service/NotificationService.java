package com.company.kanban.service;

import com.company.kanban.dto.NotificationResponse;
import com.company.kanban.entity.*;
import com.company.kanban.repository.NotificationRepository;
import com.company.kanban.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    static final int CHAT_ALERT_QUIET_MINUTES = 5;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(User currentUser, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return notificationRepository
                .findByRecipientIdAndClearedAtIsNullOrderByCreatedAtDesc(currentUser.getId(), PageRequest.of(0, limit))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount(User currentUser) {
        return Map.of("count", notificationRepository.countByRecipientIdAndReadFalseAndClearedAtIsNull(currentUser.getId()));
    }

    @Transactional
    public NotificationResponse markRead(Long id, User currentUser) {
        Notification notification = owned(id, currentUser);
        notification.markRead();
        // Flush before returning so a subsequent panel refresh can never read
        // the old value from a still-pending persistence context.
        notificationRepository.saveAndFlush(notification);
        return toResponse(notification);
    }

    @Transactional
    public void markAllRead(User currentUser) {
        List<Notification> unread = notificationRepository
                .findByRecipientIdAndReadFalseAndClearedAtIsNull(currentUser.getId());
        unread.forEach(Notification::markRead);
        notificationRepository.saveAllAndFlush(unread);
    }

    @Transactional
    public void clear(Long id, User currentUser) {
        Notification notification = owned(id, currentUser);
        notification.clear();
        notificationRepository.saveAndFlush(notification);
    }

    @Transactional
    public void clearAll(User currentUser) {
        List<Notification> visible = notificationRepository
                .findByRecipientIdAndClearedAtIsNull(currentUser.getId());
        visible.forEach(Notification::clear);
        notificationRepository.saveAllAndFlush(visible);
    }

    public void notifyTaskAssigned(Task task, User actor) {
        User assignee = task.getAssignee();
        if (assignee != null) notifyUser(assignee, actor, NotificationType.TASK_ASSIGNED,
                "New task assigned", actor.getName() + " assigned \"" + task.getTitle() + "\" to you.", task, null);
    }

    public void notifyTaskUpdated(Task task, User actor, String changeSummary) {
        String message = changeSummary + " for \"" + task.getTitle() + "\" was changed by " + actor.getName() + ".";
        notifyUser(task.getAssignee(), actor, NotificationType.TASK_UPDATED, "Task updated", message, task, null);
        if (task.getCreatedBy() != null && (task.getAssignee() == null || !task.getCreatedBy().getId().equals(task.getAssignee().getId())))
            notifyUser(task.getCreatedBy(), actor, NotificationType.TASK_UPDATED, "Task updated", message, task, null);
    }

    public void notifyTaskReassigned(Task task, User oldAssignee, User newAssignee, User actor) {
        if (newAssignee != null && (oldAssignee == null || !oldAssignee.getId().equals(newAssignee.getId())))
            notifyUser(newAssignee, actor, NotificationType.TASK_REASSIGNED, "Task reassigned to you",
                    "\"" + task.getTitle() + "\" has been reassigned to you.", task, null);
    }

    public void notifyReviewSubmitted(Task task, User actor) {
        userRepository.findByDepartmentIdAndRole(task.getDepartment().getId(), Role.MANAGER)
                .forEach(manager -> notifyUser(manager, actor, NotificationType.TASK_REVIEW_SUBMITTED, "Review requested",
                        actor.getName() + " submitted \"" + task.getTitle() + "\" for review.", task, null));
    }

    public void notifyReviewResult(Task task, User actor, boolean approved) {
        notifyUser(task.getAssignee(), actor,
                approved ? NotificationType.TASK_APPROVED : NotificationType.TASK_REVIEW_RETURNED,
                approved ? "Task approved" : "Task returned",
                actor.getName() + (approved ? " approved \"" : " returned \"") + task.getTitle()
                        + (approved ? "\"." : "\" to Doing."), task, null);
    }

    public void notifyProjectCreated(Board board, User actor) {
        userRepository.findByDepartmentIdOrderByNameAsc(board.getDepartment().getId()).forEach(user ->
                notifyUser(user, actor, NotificationType.PROJECT_CREATED, "New project",
                        "\"" + board.getName() + "\" was created in " + board.getDepartment().getName() + " by " + actor.getName() + ".",
                        null, board.getId()));
    }

    public void notifyDailyReportSubmitted(DailyWorkReport report, User actor) {
        if (actor.getRole() != Role.STAFF) return;
        String date = report.getReportDate().format(DateTimeFormatter.ofPattern("d MMM uuuu"));
        userRepository.findByDepartmentIdAndRole(actor.getDepartment().getId(), Role.MANAGER).forEach(manager ->
                notifyUser(manager, actor, NotificationType.DAILY_REPORT_SUBMITTED, "Daily report submitted",
                        actor.getName() + " submitted their Daily Report for " + date + ".", null, null, report.getId()));
    }

    public void notifyRequestCreated(InterdepartmentRequest request, User actor) {
        userRepository.findByDepartmentIdAndRole(request.getTargetDepartment().getId(), Role.MANAGER).forEach(manager ->
                notifyRequest(manager, actor, NotificationType.REQUEST_CREATED, "New interdepartment request",
                        request.getRequestingDepartment().getName() + " requested: " + request.getTitle(), request.getId()));
    }

    public void notifyRequestStatusChanged(InterdepartmentRequest request, User actor) {
        NotificationType type = switch (request.getStatus()) {
            case ACKNOWLEDGED -> NotificationType.REQUEST_ACKNOWLEDGED;
            case IN_PROGRESS -> NotificationType.REQUEST_IN_PROGRESS;
            case COMPLETED -> NotificationType.REQUEST_COMPLETED;
            case REJECTED -> NotificationType.REQUEST_REJECTED;
            default -> null;
        };
        if (type != null) notifyRequest(request.getCreatedBy(), actor, type, "Interdepartment request updated",
                request.getTitle() + " is now " + request.getStatus().name().replace('_', ' ').toLowerCase() + ".", request.getId());
    }

    public void notifyChatMessage(User recipient, User actor, String title, String message, String destination) {
        if (recipient == null || actor == null || recipient.getStatus() != AccountStatus.ACTIVE || recipient.getId().equals(actor.getId())) return;
        // A busy chat should not flood the bell and desktop: while an alert for this chat from the
        // last few minutes is still unread, further messages do not add another one.
        if (notificationRepository.existsByRecipientIdAndTypeAndDestinationAndReadFalseAndClearedAtIsNullAndCreatedAtAfter(
                recipient.getId(), NotificationType.CHAT_MESSAGE, destination, LocalDateTime.now().minusMinutes(CHAT_ALERT_QUIET_MINUTES))) return;
        Notification notification = new Notification(recipient, NotificationType.CHAT_MESSAGE, limit(title, 120), limit(message, 700), null, null, null);
        notification.setDestination(destination);
        notificationRepository.save(notification);
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private void notifyRequest(User recipient, User actor, NotificationType type, String title, String message, Long requestId) {
        if (recipient == null || actor == null || recipient.getStatus() != AccountStatus.ACTIVE || recipient.getId().equals(actor.getId())) return;
        Notification notification = new Notification(recipient, type, title, message, requestId);
        notification.setDestination("/requests/" + requestId);
        notificationRepository.save(notification);
    }

    private void notifyUser(User recipient, User actor, NotificationType type, String title, String message, Task task, Long boardId) {
        notifyUser(recipient, actor, type, title, message, task, boardId, null);
    }

    private void notifyUser(User recipient, User actor, NotificationType type, String title, String message,
                            Task task, Long boardId, Long dailyReportId) {
        if (recipient == null || actor == null || recipient.getStatus() != AccountStatus.ACTIVE || recipient.getId().equals(actor.getId())) return;
        Notification notification = new Notification(recipient, type, title, message,
                task == null ? null : task.getId(),
                task == null ? boardId : task.isGeneralTask() ? null : task.getColumn().getBoard().getId(), dailyReportId);
        notification.setDestination(destination(type, notification.getTaskId(), notification.getBoardId(), dailyReportId, null));
        notificationRepository.save(notification);
    }

    private Notification owned(Long id, User currentUser) {
        return notificationRepository.findByIdAndRecipientIdAndClearedAtIsNull(id, currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.isRead(),
                n.getCreatedAt(), n.getTaskId(), n.getBoardId(), n.getDailyReportId(), n.getRawMaterialArrivalId(),
                n.getRequestId(),
                n.getDestination() == null ? destination(n.getType(), n.getTaskId(), n.getBoardId(), n.getDailyReportId(), n.getRawMaterialArrivalId()) : n.getDestination());
    }

    public void notifyScheduled(User recipient, NotificationType type, String title, String message,
                                Long taskId, Long boardId, String destination) {
        if (recipient == null || recipient.getStatus() != AccountStatus.ACTIVE) return;
        Notification notification = new Notification(recipient, type, title, message, taskId, boardId, null);
        notification.setDestination(destination == null ? destination(type, taskId, boardId, null, null) : destination);
        notificationRepository.save(notification);
    }

    public static String destination(NotificationType type, Long taskId, Long boardId, Long dailyReportId, Long arrivalId) {
        if (arrivalId != null) return "/ppc/raw-material-arrivals?arrivalId=" + arrivalId;
        if (type == NotificationType.TASK_REVIEW_SUBMITTED) return taskId == null ? "/reviews" : "/reviews?taskId=" + taskId;
        if (type == NotificationType.DAILY_REPORT_SUBMITTED) return dailyReportId == null ? "/reports/monthly" : "/reports/monthly?dailyReportId=" + dailyReportId;
        if (type == NotificationType.MONTHLY_REPORT_3_DAY_REMINDER || type == NotificationType.MONTHLY_REPORT_MONTH_END || type == NotificationType.MONTHLY_REPORT_OVERDUE)
            return "/reports/monthly";
        if (boardId != null) return "/projects?boardId=" + boardId + (taskId == null ? "" : "&taskId=" + taskId);
        return taskId == null ? "/" : "/?taskId=" + taskId;
    }
}
