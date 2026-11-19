package com.company.kanban.repository;

import com.company.kanban.entity.MachinePart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MachinePartRepository extends JpaRepository<MachinePart, Long> {
    List<MachinePart> findByMachineIdOrderByInstalledOnDescIdDesc(Long machineId);
    List<MachinePart> findByReplacedOnIsNull();
    List<MachinePart> findByMachineIdAndReplacedOnIsNullAndPartNameIgnoreCase(Long machineId, String partName);
}
