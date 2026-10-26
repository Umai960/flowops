package com.company.kanban.repository;

import com.company.kanban.entity.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {
    Optional<ChatParticipant> findByThreadIdAndUserId(Long threadId, Long userId);
    boolean existsByThreadIdAndUserId(Long threadId, Long userId);

    @Query("select p from ChatParticipant p join fetch p.user u left join fetch u.department where p.thread.id = :threadId order by p.joinedAt, p.id")
    List<ChatParticipant> findByThreadIdWithUser(@Param("threadId") Long threadId);

    @Query("select p.thread.id, count(m) from ChatParticipant p, ChatMessage m where m.thread = p.thread and p.user.id = :userId" +
            " and m.id > coalesce(p.lastReadMessageId, 0) and m.sender.id <> :userId and m.deletedAt is null group by p.thread.id")
    List<Object[]> countUnreadByThread(@Param("userId") Long userId);

    @Modifying
    @Query("delete from ChatParticipant p where p.thread.id in :threadIds")
    int deleteByThreadIds(@Param("threadIds") List<Long> threadIds);

    @Modifying
    @Query("delete from ChatParticipant p where p.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
