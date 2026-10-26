package com.company.kanban.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * File bytes for a chat message. Stored in PostgreSQL (bytea) so files are included in
 * the normal FlowOps backups and removed together with their message.
 */
@Entity
@Table(name = "chat_attachments")
public class ChatAttachment {
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, unique = true)
    private ChatMessage message;

    // Plain bytea (not a PostgreSQL large object), so deleting the row also frees the file.
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    protected ChatAttachment() {}

    public ChatAttachment(ChatMessage message, byte[] data) {
        this.message = message;
        this.data = data;
    }

    public Long getId() { return id; }
    public ChatMessage getMessage() { return message; }
    public byte[] getData() { return data; }
}
