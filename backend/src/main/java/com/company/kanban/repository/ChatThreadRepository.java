package com.company.kanban.repository;

import com.company.kanban.entity.ChatSubjectType;
import com.company.kanban.entity.ChatThread;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {
    Optional<ChatThread> findBySubjectTypeAndSubjectId(ChatSubjectType subjectType, Long subjectId);

    @Query("select t from ChatThread t where t.lastMessageAt is not null and exists (select p.id from ChatParticipant p where p.thread = t and p.user.id = :userId) order by t.lastMessageAt desc")
    List<ChatThread> findFollowedBy(@Param("userId") Long userId, Pageable pageable);

    @Query("select t from ChatThread t where t.lastMessageAt is not null and (t.department.id = :departmentId or exists (select p.id from ChatParticipant p where p.thread = t and p.user.id = :userId)) order by t.lastMessageAt desc")
    List<ChatThread> findVisibleTo(@Param("userId") Long userId, @Param("departmentId") Long departmentId, Pageable pageable);

    @Query("select t from ChatThread t where t.lastMessageAt is not null order by t.lastMessageAt desc")
    List<ChatThread> findAllActive(Pageable pageable);

    @Query("select t.id from ChatThread t where coalesce(t.lastMessageAt, t.createdAt) < :cutoff")
    List<Long> findIdsInactiveSince(@Param("cutoff") LocalDateTime cutoff);

    @Query("select t.id from ChatThread t where (t.subjectType = com.company.kanban.entity.ChatSubjectType.TASK and not exists (select k.id from Task k where k.id = t.subjectId))" +
            " or (t.subjectType = com.company.kanban.entity.ChatSubjectType.PLANNING and not exists (select i.id from PpcPlanningItem i where i.id = t.subjectId))")
    List<Long> findOrphanIds();

    @Modifying
    @Query("delete from ChatThread t where t.id in :ids")
    int deleteByIds(@Param("ids") List<Long> ids);
}
