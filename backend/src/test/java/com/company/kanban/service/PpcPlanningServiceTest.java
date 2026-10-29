package com.company.kanban.service;

import com.company.kanban.dto.CreatePpcPlanningItemRequest;
import com.company.kanban.dto.UpdatePpcPlanningItemRequest;
import com.company.kanban.entity.Department;
import com.company.kanban.entity.PpcPlanningItem;
import com.company.kanban.entity.Role;
import com.company.kanban.entity.User;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.PpcPlanningItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PpcPlanningServiceTest {
    private final PpcPlanningItemRepository repository = mock(PpcPlanningItemRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final PpcPlanningService service = new PpcPlanningService(repository, departments, new AuthorizationService());
    private Department ppc;
    private Department prod;
    private User ppcStaff;
    private User prodStaff;
    private User admin;

    @BeforeEach
    void setUp() {
        ppc = entity(new Department("PPC"), 10L);
        prod = entity(new Department("PROD"), 20L);
        ppcStaff = entity(new User("PPC Staff", "staff@test", "x", Role.STAFF, ppc), 1L);
        prodStaff = entity(new User("Prod Staff", "prod@test", "x", Role.STAFF, prod), 3L);
        admin = entity(new User("Admin", "admin@test", "x", Role.ADMIN, prod), 2L);
        when(departments.findById(10L)).thenReturn(Optional.of(ppc));
        when(repository.save(any())).thenAnswer(invocation -> {
            PpcPlanningItem item = invocation.getArgument(0);
            if (item.getId() == null) ReflectionTestUtils.setField(item, "id", 100L);
            return item;
        });
    }

    @Test void ppcStaffCanCreateAndCreatorComesFromPrincipal() {
        var response = service.create(new CreatePpcPlanningItemRequest("  Stock plan  ", "desc", LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 12), null, null, null), ppcStaff);
        assertEquals("Stock plan", response.title());
        assertEquals(1L, response.createdById());
        assertEquals(10L, response.departmentId());
    }

    @Test void itemsKeepAChosenColourAndRejectUnknownOnes() {
        assertEquals("orange", service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null, null, " Orange "), ppcStaff).color());
        assertNull(service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null, null, ""), ppcStaff).color());
        assertThrows(ResponseStatusException.class, () -> service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null, null, "#ff0000"), ppcStaff));
    }

    @Test void adminCanCreate() { assertDoesNotThrow(() -> service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null, null, null), admin)); }

    @Test void everyDepartmentCreatesInItsOwnCalendar() {
        assertEquals(20L, service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null, null), prodStaff).departmentId());
    }

    @Test void staffCannotUseAnotherDepartmentsCalendar() {
        assertThrows(ResponseStatusException.class, () -> service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null, 10L), prodStaff));
        assertThrows(ResponseStatusException.class, () -> service.getItems(2026, 8, 10L, prodStaff));
        verify(repository, never()).save(any());
        verify(repository, never()).findIntersecting(any(), any(), any());
    }

    @Test void adminCanChooseDepartment() {
        assertEquals(10L, service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null, 10L), admin).departmentId());
        service.getItems(2026, 8, 10L, admin);
        verify(repository).findIntersecting(10L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    @Test void otherDepartmentsItemsCannotBeEditedOrDeleted() {
        PpcPlanningItem item = item("PPC only", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1));
        when(repository.findById(100L)).thenReturn(Optional.of(item));
        assertThrows(ResponseStatusException.class, () -> service.update(100L, new UpdatePpcPlanningItemRequest("Changed", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null), prodStaff));
        assertThrows(ResponseStatusException.class, () -> service.delete(100L, prodStaff));
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    @Test void titleAndDateValidation() {
        assertThrows(ResponseStatusException.class, () -> service.create(new CreatePpcPlanningItemRequest("  ", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null, null), ppcStaff));
        assertThrows(ResponseStatusException.class, () -> service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1), null, null, null), ppcStaff));
    }

    @Test void retrievalUsesIntersectionForSingleAndCrossMonthItems() {
        PpcPlanningItem single = item("Single", LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 12));
        PpcPlanningItem crossing = item("Crossing", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 2));
        when(repository.findIntersecting(10L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).thenReturn(List.of(single, crossing));
        assertEquals(2, service.getItems(2026, 8, null, ppcStaff).size());
        verify(repository).findIntersecting(10L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    @Test void updateAndDeleteWork() {
        PpcPlanningItem item = item("Old", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1));
        when(repository.findById(100L)).thenReturn(Optional.of(item));
        assertEquals("New", service.update(100L, new UpdatePpcPlanningItemRequest("New", null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3), null, "Planned"), ppcStaff).title());
        service.delete(100L, ppcStaff); verify(repository).delete(item);
    }

    private PpcPlanningItem item(String title, LocalDate start, LocalDate end) { PpcPlanningItem item = new PpcPlanningItem(title, null, start, end, null, null, ppcStaff); return entity(item, 100L); }
    private <T> T entity(T value, Long id) { ReflectionTestUtils.setField(value, "id", id); return value; }
}
