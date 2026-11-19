package com.company.kanban.repository;

import com.company.kanban.entity.MachineBreakdown;
import com.company.kanban.entity.MachineBreakdownStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MachineBreakdownRepository extends JpaRepository<MachineBreakdown, Long> {
    List<MachineBreakdown> findByMachineIdOrderByReportedAtDesc(Long machineId);
    List<MachineBreakdown> findByStatusInOrderByReportedAtDesc(Collection<MachineBreakdownStatus> statuses);
    List<MachineBreakdown> findTop200ByStatusOrderByCompletedAtDesc(MachineBreakdownStatus status);
    boolean existsByMachineIdAndStatusInAndIdNot(Long machineId, Collection<MachineBreakdownStatus> statuses, Long id);
}
