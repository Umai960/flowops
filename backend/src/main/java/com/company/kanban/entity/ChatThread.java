package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One chat (comment space) per task or planning item. Created when the first message is sent or the first person is added. */
@Entity
@Table(name = "chat_threads", uniqueConstraints = @UniqueConstraint(name = "uk_chat_thread_subject", columnNames = {"subject_type", "subject_id"}),
        indexes = @Index(name = "idx_chat_thread_last_message", columnList = "last_message_at"))
public class ChatThread {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private ChatSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    // Copied from the task or planning item so access checks and lists do not need to load the subject.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 200)
    private String lastMessagePreview;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    protected ChatThread() {}

    public ChatThread(ChatSubjectType subjectType, Long subjectId, String title, Department department) {
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.title = title;
        this.department = department;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public ChatSubjectType getSubjectType() { return subjectType; }
    public Long getSubjectId() { return subjectId; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void recordMessage(String preview, LocalDateTime at) { this.lastMessagePreview = preview; this.lastMessageAt = at; }
    public void setLastMessagePreview(String preview) { this.lastMessagePreview = preview; }
}
