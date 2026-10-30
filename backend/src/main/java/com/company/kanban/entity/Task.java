package com.company.kanban.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private Integer workload;

    @Column(length = 16)
    private String color;

    private LocalDate dueDate;

    @Column(nullable = false)
    private Integer position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "column_id")
    private KanbanColumn column;

    // Set only for general tasks. Project tasks continue to derive their
    // authoritative department from column -> board -> department.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime submittedForReviewAt;

    /** Reliable completion event timestamp; null for legacy tasks completed before this field existed. */
    private LocalDateTime completedAt;

    public Task() {
    }

    public Task(
            String title,
            String description,
            Priority priority,
            LocalDate dueDate,
            Integer position,
            KanbanColumn column,
            User assignee) {

        this.title = title;
        this.description = description;
        this.priority = priority;
        this.dueDate = dueDate;
        this.position = position;
        this.column = column;
        this.assignee = assignee;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Integer getWorkload() {
        return workload;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Integer getPosition() {
        return position;
    }

    public KanbanColumn getColumn() {
        return column;
    }

    public Department getDepartment() {
        if (department != null) {
            return department;
        }
        return column == null ? null : column.getBoard().getDepartment();
    }

    public boolean isGeneralTask() {
        return column == null;
    }

    public User getAssignee() {
        return assignee;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getSubmittedForReviewAt() { return submittedForReviewAt; }
    public void setSubmittedForReviewAt(LocalDateTime value) { this.submittedForReviewAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public void setColumn(KanbanColumn column) {
        this.column = column;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getColor() { return color; }

    public void setColor(String color) { this.color = color; }

    public void setWorkload(Integer workload) {
        this.workload = workload;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
