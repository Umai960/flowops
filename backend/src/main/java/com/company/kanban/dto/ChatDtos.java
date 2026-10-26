package com.company.kanban.dto;

import com.company.kanban.entity.ChatSubjectType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatPerson(Long id, String name, String departmentName, String role) {}
    public record ChatAttachmentInfo(String name, String contentType, long size, boolean image) {}
    public record ChatMessageResponse(Long id, ChatSubjectType subjectType, Long subjectId, Long senderId, String senderName,
                                      String senderDepartment, String body, ChatAttachmentInfo attachment,
                                      LocalDateTime createdAt, boolean deleted) {}
    public record ChatView(ChatSubjectType subjectType, Long subjectId, String title, String departmentName,
                           List<ChatPerson> participants, List<ChatMessageResponse> messages, boolean hasMore) {}
    public record ChatThreadSummary(ChatSubjectType subjectType, Long subjectId, String title, String departmentName,
                                    String lastMessagePreview, LocalDateTime lastMessageAt, long unreadCount) {}
    public record AddParticipantRequest(@NotNull Long userId) {}
    /** Pushed to open browsers over the live stream. kind is MESSAGE, DELETED or PARTICIPANTS. */
    public record ChatEvent(String kind, ChatSubjectType subjectType, Long subjectId, ChatMessageResponse message) {}
}
