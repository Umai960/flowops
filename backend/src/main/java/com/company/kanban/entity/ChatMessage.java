package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_message_thread", columnList = "thread_id,id"),
        @Index(name = "idx_chat_message_created", columnList = "created_at")
})
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private ChatThread thread;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(length = 4000)
    private String body;

    // File details are kept here; the file bytes live in ChatAttachment so message lists never load them.
    @Column(length = 255)
    private String attachmentName;

    @Column(length = 150)
    private String attachmentContentType;

    private Long attachmentSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    protected ChatMessage() {}

    public ChatMessage(ChatThread thread, User sender, String body) {
        this.thread = thread;
        this.sender = sender;
        this.body = body;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public ChatThread getThread() { return thread; }
    public User getSender() { return sender; }
    public String getBody() { return body; }
    public String getAttachmentName() { return attachmentName; }
    public String getAttachmentContentType() { return attachmentContentType; }
    public Long getAttachmentSize() { return attachmentSize; }
    public boolean hasAttachment() { return attachmentName != null; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }

    public void attach(String name, String contentType, long size) {
        this.attachmentName = name;
        this.attachmentContentType = contentType;
        this.attachmentSize = size;
    }

    /** Deleted messages stay as a placeholder so the conversation keeps its order. */
    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
        this.body = null;
        this.attachmentName = null;
        this.attachmentContentType = null;
        this.attachmentSize = null;
    }
}
