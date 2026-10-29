package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interdepartment_requests", indexes = {
        @Index(name = "idx_request_target_status", columnList = "target_department_id,status"),
        @Index(name = "idx_request_source_created", columnList = "requesting_department_id,created_at"),
        @Index(name = "idx_request_created_at", columnList = "created_at")
})
public class InterdepartmentRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requesting_department_id", nullable = false) private Department requestingDepartment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "target_department_id", nullable = false) private Department targetDepartment;
    // A second receiving department (PROD_1) that sees and handles RDD requests together with PPC.
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "co_target_department_id") private Department coTargetDepartment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_id", nullable = false) private User createdBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_to_id") private User assignedTo;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 4000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Priority priority;
    private LocalDate neededBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InterdepartmentRequestStatus status = InterdepartmentRequestStatus.REQUESTED;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime completedAt;
    @Column(length = 1000) private String rejectionReason;
    private Long linkedTaskId;
    private LocalDateTime archivedAt;
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("changedAt asc") private List<InterdepartmentRequestStatusHistory> statusHistory = new ArrayList<>();

    protected InterdepartmentRequest() {}
    public InterdepartmentRequest(Department source, Department target, User creator, String title, String description, Priority priority, LocalDate neededBy) {
        this.requestingDepartment = source; this.targetDepartment = target; this.createdBy = creator; this.title = title; this.description = description; this.priority = priority; this.neededBy = neededBy;
    }
    @PrePersist void create() { if (createdAt == null) createdAt = LocalDateTime.now(); if (updatedAt == null) updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
    public Long getId(){return id;} public Department getRequestingDepartment(){return requestingDepartment;} public Department getTargetDepartment(){return targetDepartment;} public Department getCoTargetDepartment(){return coTargetDepartment;} public User getCreatedBy(){return createdBy;} public User getAssignedTo(){return assignedTo;} public String getTitle(){return title;} public String getDescription(){return description;} public Priority getPriority(){return priority;} public LocalDate getNeededBy(){return neededBy;} public InterdepartmentRequestStatus getStatus(){return status;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;} public LocalDateTime getAcknowledgedAt(){return acknowledgedAt;} public LocalDateTime getCompletedAt(){return completedAt;} public String getRejectionReason(){return rejectionReason;} public Long getLinkedTaskId(){return linkedTaskId;} public LocalDateTime getArchivedAt(){return archivedAt;} public List<InterdepartmentRequestStatusHistory> getStatusHistory(){return statusHistory;}
    public void setStatus(InterdepartmentRequestStatus value){status=value;} public void setCoTargetDepartment(Department value){coTargetDepartment=value;} public void setAssignedTo(User value){assignedTo=value;} public void setAcknowledgedAt(LocalDateTime value){acknowledgedAt=value;} public void setCompletedAt(LocalDateTime value){completedAt=value;} public void setRejectionReason(String value){rejectionReason=value;} public void setLinkedTaskId(Long value){linkedTaskId=value;} public void setArchivedAt(LocalDateTime value){archivedAt=value;}
}
