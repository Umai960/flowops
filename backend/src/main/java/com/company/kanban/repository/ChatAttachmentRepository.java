package com.company.kanban.repository;

import com.company.kanban.entity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {
    Optional<ChatAttachment> findByMessageId(Long messageId);

    @Modifying
    @Query("delete from ChatAttachment a where a.message.id = :messageId")
    int deleteByMessageId(@Param("messageId") Long messageId);

    @Modifying
    @Query("delete from ChatAttachment a where a.message.id in (select m.id from ChatMessage m where m.createdAt < :cutoff)")
    int deleteForMessagesOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("delete from ChatAttachment a where a.message.id in (select m.id from ChatMessage m where m.thread.id in :threadIds)")
    int deleteByThreadIds(@Param("threadIds") List<Long> threadIds);

    @Modifying
    @Query("delete from ChatAttachment a where a.message.id in (select m.id from ChatMessage m where m.sender.id = :userId)")
    int deleteBySenderId(@Param("userId") Long userId);
}
