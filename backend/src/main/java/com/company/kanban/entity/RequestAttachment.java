package com.company.kanban.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * A file attached to an interdepartment request. Stored in PostgreSQL (bytea) so files are
 * included in the normal FlowOps backups. Unlike chat files, these are kept for as long as
 * the request exists.
 */
@Entity
@Table(name = "request_attachments", indexes = @Index(name = "idx_request_attachment_request", columnList = "request_id"))
public class RequestAttachment {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_PER_REQUEST = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private InterdepartmentRequest request;

    @Column(nullable = false, length = 200)
    private String fileName;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    // Kept as plain values (no foreign key) so the uploader's account can still be deleted later.
    private Long uploadedById;

    @Column(length = 160)
    private String uploadedByName;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    protected RequestAttachment() {}

    public RequestAttachment(InterdepartmentRequest request, String fileName, String contentType, byte[] data, User uploadedBy) {
        this.request = request;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = data.length;
        this.data = data;
        this.uploadedById = uploadedBy.getId();
        this.uploadedByName = uploadedBy.getName();
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public InterdepartmentRequest getRequest() { return request; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Long getUploadedById() { return uploadedById; }
    public String getUploadedByName() { return uploadedByName; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public byte[] getData() { return data; }
}
