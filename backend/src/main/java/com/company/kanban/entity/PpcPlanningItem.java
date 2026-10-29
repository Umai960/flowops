package com.company.kanban.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ppc_planning_items")
public class PpcPlanningItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean allDay = true;

    @Column(length = 32)
    private String priority;

    @Column(length = 32)
    private String status;

    /** Optional colour key chosen by the user so items on the same day are easy to tell apart. */
    @Column(length = 16)
    private String color;

    // Reserved for a future additive link to an existing General Task.
    @Column(name = "linked_task_id")
    private Long linkedTaskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    // Each department keeps its own calendar. Nullable only so existing rows can be backfilled by PlanningDepartmentMigration.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected PpcPlanningItem() { }

    public PpcPlanningItem(String title, String description, LocalDate startDate,
                            LocalDate endDate, String priority, String status, User createdBy) {
        this(title, description, startDate, endDate, priority, status, createdBy, createdBy.getDepartment());
    }

    public PpcPlanningItem(String title, String description, LocalDate startDate,
                            LocalDate endDate, String priority, String status, User createdBy, Department department) {
        this.department = department;
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.priority = priority;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public boolean isAllDay() { return allDay; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Long getLinkedTaskId() { return linkedTaskId; }
    public void setLinkedTaskId(Long linkedTaskId) { this.linkedTaskId = linkedTaskId; }
    public User getCreatedBy() { return createdBy; }
    public Department getDepartment() { return department; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
