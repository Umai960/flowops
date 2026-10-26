package com.company.kanban.repository;

import com.company.kanban.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdAndClearedAtIsNullOrderByCreatedAtDesc(Long recipientId, Pageable pageable);
    long countByRecipientIdAndReadFalseAndClearedAtIsNull(Long recipientId);
    Optional<Notification> findByIdAndRecipientIdAndClearedAtIsNull(Long id, Long recipientId);
    List<Notification> findByRecipientIdAndReadFalseAndClearedAtIsNull(Long recipientId);
    List<Notification> findByRecipientIdAndClearedAtIsNull(Long recipientId);
    void deleteByRecipientId(Long recipientId);
    boolean existsByRecipientIdAndTypeAndDestinationAndReadFalseAndClearedAtIsNullAndCreatedAtAfter(
            Long recipientId, com.company.kanban.entity.NotificationType type, String destination, LocalDateTime after);
    List<Notification> findByRecipientIdAndIdGreaterThanOrderByIdAsc(Long recipientId, Long after, Pageable pageable);
    @Query("select max(n.id) from Notification n where n.recipient.id = :recipientId")
    Optional<Long> findMaxIdByRecipientId(@Param("recipientId") Long recipientId);
    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
