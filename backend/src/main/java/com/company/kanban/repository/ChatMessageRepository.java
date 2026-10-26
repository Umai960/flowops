package com.company.kanban.repository;

import com.company.kanban.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("select m from ChatMessage m join fetch m.sender s left join fetch s.department where m.thread.id = :threadId and m.id < :beforeId order by m.id desc")
    List<ChatMessage> findPageBefore(@Param("threadId") Long threadId, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("select m from ChatMessage m join fetch m.sender s left join fetch s.department where m.thread.id = :threadId and m.id > :afterId order by m.id asc")
    List<ChatMessage> findAfter(@Param("threadId") Long threadId, @Param("afterId") Long afterId, Pageable pageable);

    @Query("select max(m.id) from ChatMessage m where m.thread.id = :threadId")
    Long findLatestId(@Param("threadId") Long threadId);

    @Modifying
    @Query("delete from ChatMessage m where m.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("delete from ChatMessage m where m.thread.id in :threadIds")
    int deleteByThreadIds(@Param("threadIds") List<Long> threadIds);

    @Modifying
    @Query("delete from ChatMessage m where m.sender.id = :userId")
    int deleteBySenderId(@Param("userId") Long userId);
}
