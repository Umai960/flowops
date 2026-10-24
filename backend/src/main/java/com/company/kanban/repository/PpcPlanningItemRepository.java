package com.company.kanban.repository;

import com.company.kanban.entity.PpcPlanningItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PpcPlanningItemRepository extends JpaRepository<PpcPlanningItem, Long> {
    @Query("select item from PpcPlanningItem item where item.department.id = :departmentId and item.startDate <= :monthEnd and item.endDate >= :monthStart order by item.startDate, item.id")
    List<PpcPlanningItem> findIntersecting(@Param("departmentId") Long departmentId, @Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);

    @Query("select item from PpcPlanningItem item where upper(item.department.name) = upper(:departmentName) and item.endDate >= :from order by item.startDate, item.id")
    List<PpcPlanningItem> findUpcoming(@Param("departmentName") String departmentName, @Param("from") LocalDate from, org.springframework.data.domain.Pageable pageable);
}
