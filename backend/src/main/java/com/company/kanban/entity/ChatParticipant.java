package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A person who follows a chat: they see it in their chat list and are notified of new messages. */
@Entity
@Table(name = "chat_participants", uniqueConstraints = @UniqueConstraint(name = "uk_chat_participant", columnNames = {"thread_id", "user_id"}))
public class ChatParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private ChatThread thread;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private Long lastReadMessageId;

    protected ChatParticipant() {}

    public ChatParticipant(ChatThread thread, User user) {
        this.thread = thread;
        this.user = user;
        this.joinedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public ChatThread getThread() { return thread; }
    public User getUser() { return user; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public Long getLastReadMessageId() { return lastReadMessageId; }
    public void markReadUpTo(Long messageId) {
        if (messageId != null && (lastReadMessageId == null || messageId > lastReadMessageId)) lastReadMessageId = messageId;
    }
}
