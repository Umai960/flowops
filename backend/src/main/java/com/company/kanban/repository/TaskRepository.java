package com.company.kanban.repository;

import com.company.kanban.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;
import com.company.kanban.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskRepository
        extends JpaRepository<Task, Long> {

    List<Task> findByColumnIdOrderByPositionAsc(Long columnId);

    @org.springframework.data.jpa.repository.Query("select t from Task t where t.column.id = :columnId and (t.status <> com.company.kanban.entity.TaskStatus.DONE or t.completedAt is null or t.completedAt >= :cutoff) order by t.position")
    List<Task> findActiveByColumnId(Long columnId, LocalDateTime cutoff);

    @org.springframework.data.jpa.repository.Query("select t from Task t where t.assignee.id = :assigneeId and (t.status <> com.company.kanban.entity.TaskStatus.DONE or t.completedAt is null or t.completedAt >= :cutoff) order by t.status, t.position")
    List<Task> findActiveByAssigneeId(Long assigneeId, LocalDateTime cutoff);

    @org.springframework.data.jpa.repository.Query("select t from Task t left join t.column c left join c.board b where (b.department.id = :departmentId or t.department.id = :departmentId) and (t.status <> com.company.kanban.entity.TaskStatus.DONE or t.completedAt is null or t.completedAt >= :cutoff) order by t.status, t.position")
    List<Task> findActiveByEffectiveDepartmentId(Long departmentId, LocalDateTime cutoff);

    @org.springframework.data.jpa.repository.Query(value = "select t from Task t left join t.column c left join c.board b where t.status = com.company.kanban.entity.TaskStatus.DONE and ((:includeLegacy = true and t.completedAt is null) or (t.completedAt >= :fromDate and t.completedAt < :toDate)) and (:assigneeId = 0 or t.assignee.id = :assigneeId) and (:departmentId = 0 or b.department.id = :departmentId or t.department.id = :departmentId) order by case when t.completedAt is null then 1 else 0 end, t.completedAt desc, t.id desc",
            countQuery = "select count(t) from Task t left join t.column c left join c.board b where t.status = com.company.kanban.entity.TaskStatus.DONE and ((:includeLegacy = true and t.completedAt is null) or (t.completedAt >= :fromDate and t.completedAt < :toDate)) and (:assigneeId = 0 or t.assignee.id = :assigneeId) and (:departmentId = 0 or b.department.id = :departmentId or t.department.id = :departmentId)")
    Page<Task> findCompletedHistory(LocalDateTime fromDate, LocalDateTime toDate, Long assigneeId, Long departmentId, boolean includeLegacy, Pageable pageable);

    List<Task> findByColumnIdAndIdNotOrderByPositionAsc(
            Long columnId,
            Long taskId
    );

    List<Task> findByAssigneeIdOrderByStatusAscPositionAsc(Long assigneeId);

    @org.springframework.data.jpa.repository.Query("""
            select distinct t from Task t
            left join fetch t.column c
            left join fetch c.board b
            left join fetch b.department bd
            left join fetch t.department td
            left join fetch t.assignee a
            left join fetch t.createdBy cb
            where a.id = :assigneeId
            order by t.id
            """)
    List<Task> findDetailedByAssigneeId(Long assigneeId);

    List<Task> findByStatusOrderBySubmittedForReviewAtAsc(com.company.kanban.entity.TaskStatus status);

    int countByColumnId(Long columnId);

    int countByColumnIsNullAndStatus(TaskStatus status);

    List<Task> findByColumnIsNullAndStatusAndIdNotOrderByPositionAsc(TaskStatus status, Long taskId);

    boolean existsByColumnBoardId(Long boardId);

    boolean existsByAssigneeIdOrCreatedById(Long assigneeId, Long createdById);

    @org.springframework.data.jpa.repository.Query("""
            select distinct t from Task t
            left join fetch t.column c
            left join fetch c.board b
            left join fetch b.department bd
            left join fetch t.department td
            left join fetch t.assignee a
            left join fetch t.createdBy cb
            where bd.id = :departmentId or td.id = :departmentId
            order by t.status, t.position
            """)
    List<Task> findByEffectiveDepartmentId(Long departmentId);

    @org.springframework.data.jpa.repository.Query("""
            select distinct t from Task t
            join fetch t.column c
            join fetch c.board b
            join fetch b.department bd
            left join fetch t.assignee a
            left join fetch t.createdBy cb
            order by t.id
            """)
    List<Task> findAllProjectTasks();

    @org.springframework.data.jpa.repository.Query("""
            select distinct t from Task t
            left join fetch t.column c
            left join fetch c.board b
            left join fetch b.department d
            left join fetch t.department td
            left join fetch t.assignee a
            left join fetch t.createdBy cb
            where t.status <> com.company.kanban.entity.TaskStatus.DONE
               or (t.updatedAt >= :from and t.updatedAt < :to)
            order by t.id
            """)
    List<Task> findRelevantForSnapshot(LocalDateTime from, LocalDateTime to);
}
