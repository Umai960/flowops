package com.company.kanban.service;

import com.company.kanban.dto.SnapshotBatchResponse;
import com.company.kanban.dto.SnapshotDateResponse;
import com.company.kanban.dto.TaskSnapshotResponse;
import com.company.kanban.entity.*;
import com.company.kanban.repository.SnapshotBatchRepository;
import com.company.kanban.repository.TaskRepository;
import com.company.kanban.repository.TaskSnapshotRepository;
import com.company.kanban.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskSnapshotService {
    public static final ZoneId COMPANY_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private final SnapshotBatchRepository batchRepository;
    private final TaskSnapshotRepository snapshotRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskSnapshotService(SnapshotBatchRepository batchRepository, TaskSnapshotRepository snapshotRepository,
                               TaskRepository taskRepository, UserRepository userRepository) {
        this.batchRepository = batchRepository;
        this.snapshotRepository = snapshotRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public synchronized SnapshotBatchResponse createSnapshot(LocalDate date, SnapshotType type, boolean recovered) {
        var existing = batchRepository.findBySnapshotDateAndSnapshotType(date, type);
        if (existing.isPresent() && existing.get().getStatus() == SnapshotBatchStatus.COMPLETED) {
            return SnapshotBatchResponse.from(existing.get());
        }
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Snapshot batch previously failed; inspect it before retrying");
        }

        LocalTime cutoff = type == SnapshotType.START_OF_DAY ? LocalTime.of(8, 0) : LocalTime.of(17, 0);
        SnapshotBatch batch = batchRepository.save(new SnapshotBatch(date, type, LocalDateTime.of(date, cutoff), recovered));
        try {
            LocalDateTime from = date.atStartOfDay();
            LocalDateTime to = date.plusDays(1).atStartOfDay();
            var snapshots = taskRepository.findRelevantForSnapshot(from, to).stream()
                    .map(task -> new TaskSnapshot(batch, task)).toList();
            snapshotRepository.saveAll(snapshots);
            batch.complete(LocalDateTime.now(COMPANY_ZONE), snapshots.size());
            return SnapshotBatchResponse.from(batchRepository.save(batch));
        } catch (RuntimeException ex) {
            batch.fail(LocalDateTime.now(COMPANY_ZONE), ex.getMessage());
            batchRepository.save(batch);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<SnapshotDateResponse> getDates() {
        Map<LocalDate, Set<SnapshotType>> byDate = batchRepository.findAllByOrderBySnapshotDateDescSnapshotTypeAsc().stream()
                .filter(b -> b.getStatus() == SnapshotBatchStatus.COMPLETED)
                .collect(Collectors.groupingBy(SnapshotBatch::getSnapshotDate,
                        LinkedHashMap::new, Collectors.mapping(SnapshotBatch::getSnapshotType, Collectors.toSet())));
        return byDate.entrySet().stream().map(e -> new SnapshotDateResponse(e.getKey(),
                e.getValue().contains(SnapshotType.START_OF_DAY), e.getValue().contains(SnapshotType.END_OF_DAY))).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskSnapshotResponse> getHistory(LocalDate date, SnapshotType requestedType, User currentUser) {
        SnapshotBatch batch = getCompletedBatch(date, requestedType);
        return snapshotRepository.findByBatchIdOrderByPositionAsc(batch.getId()).stream()
                .filter(snapshot -> canSeeSnapshot(currentUser, snapshot)).map(TaskSnapshotResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskSnapshotResponse> getUserHistory(LocalDate date, SnapshotType requestedType, Long userId, User currentUser) {
        User target = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (currentUser == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        if (currentUser.getRole() == Role.STAFF && !Objects.equals(currentUser.getId(), target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff can only view their own history");
        }
        if (currentUser.getRole() == Role.MANAGER && (target.getDepartment() == null || currentUser.getDepartment() == null
                || !Objects.equals(currentUser.getDepartment().getId(), target.getDepartment().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HODs can only view their department's history");
        }
        SnapshotBatch batch = getCompletedBatch(date, requestedType);
        return snapshotRepository.findByBatchIdAndAssigneeIdOrderByStatusAscPositionAsc(batch.getId(), userId)
                .stream().map(TaskSnapshotResponse::from).toList();
    }

    private SnapshotBatch getCompletedBatch(LocalDate date, SnapshotType type) {
        SnapshotType selected = type == null ? SnapshotType.END_OF_DAY : type;
        return batchRepository.findBySnapshotDateAndSnapshotType(date, selected)
                .filter(b -> b.getStatus() == SnapshotBatchStatus.COMPLETED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Completed snapshot not found"));
    }

    private boolean canSeeSnapshot(User currentUser, TaskSnapshot snapshot) {
        if (currentUser == null) return false;
        if (currentUser.getRole() == Role.ADMIN) return true;
        if (currentUser.getRole() == Role.MANAGER) {
            return currentUser.getDepartment() != null && Objects.equals(currentUser.getDepartment().getId(), snapshot.getDepartmentId());
        }
        return Objects.equals(currentUser.getId(), snapshot.getAssigneeId());
    }
}
