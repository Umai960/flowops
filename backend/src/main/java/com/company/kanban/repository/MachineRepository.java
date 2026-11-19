package com.company.kanban.repository;

import com.company.kanban.entity.Machine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MachineRepository extends JpaRepository<Machine, Long> {
    List<Machine> findAllByOrderByCodeAsc();
    Optional<Machine> findByCodeIgnoreCase(String code);
}
