package com.company.kanban.repository;

import com.company.kanban.dto.InterdepartmentRequestResponse;
import com.company.kanban.entity.RequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RequestAttachmentRepository extends JpaRepository<RequestAttachment, Long> {
    /** File details without the file bytes, for showing the list on request cards. */
    @Query("""
            select new com.company.kanban.repository.RequestAttachmentRepository$Row(
                a.request.id, a.id, a.fileName, a.contentType, a.sizeBytes, a.uploadedById, a.uploadedByName, a.uploadedAt)
            from RequestAttachment a where a.request.id in :requestIds order by a.uploadedAt, a.id""")
    List<Row> findInfoByRequestIds(@Param("requestIds") Collection<Long> requestIds);

    long countByRequestId(Long requestId);

    record Row(Long requestId, Long id, String fileName, String contentType, long sizeBytes, Long uploadedById,
               String uploadedByName, java.time.LocalDateTime uploadedAt) {
        public InterdepartmentRequestResponse.Attachment toResponse() {
            return new InterdepartmentRequestResponse.Attachment(id, fileName, contentType, sizeBytes, uploadedById, uploadedByName, uploadedAt);
        }
    }
}
