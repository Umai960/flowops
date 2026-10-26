package com.company.kanban.service;

import com.company.kanban.dto.CreatePpcPlanningItemRequest;
import com.company.kanban.dto.PpcPlanningItemResponse;
import com.company.kanban.dto.UpdatePpcPlanningItemRequest;
import com.company.kanban.entity.Department;
import com.company.kanban.entity.PpcPlanningItem;
import com.company.kanban.entity.User;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.PpcPlanningItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/** Planning calendar for every department. Each department sees and edits only its own items; administrators can pick a department. */
@Service
public class PpcPlanningService {
    private final PpcPlanningItemRepository repository;
    private final DepartmentRepository departmentRepository;
    private final AuthorizationService authorizationService;

    public PpcPlanningService(PpcPlanningItemRepository repository, DepartmentRepository departmentRepository,
                              AuthorizationService authorizationService) {
        this.repository = repository;
        this.departmentRepository = departmentRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<PpcPlanningItemResponse> getItems(int year, int month, Long departmentId, User user) {
        Department department = resolveDepartment(departmentId, user);
        LocalDate monthStart;
        try { monthStart = LocalDate.of(year, month, 1); }
        catch (java.time.DateTimeException exception) { throw badRequest("year and month must identify a valid calendar month"); }
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        return repository.findIntersecting(department.getId(), monthStart, monthEnd).stream().map(PpcPlanningItemResponse::from).toList();
    }

    @Transactional
    public PpcPlanningItemResponse create(CreatePpcPlanningItemRequest request, User user) {
        Department department = resolveDepartment(request.departmentId(), user);
        validateDates(request.startDate(), request.endDate());
        String title = request.title().trim();
        if (title.isEmpty()) throw badRequest("Title is required");
        PpcPlanningItem item = new PpcPlanningItem(title, clean(request.description()), request.startDate(), request.endDate(),
                clean(request.priority()), clean(request.status()), user, department);
        return PpcPlanningItemResponse.from(repository.save(item));
    }

    @Transactional
    public PpcPlanningItemResponse update(Long id, UpdatePpcPlanningItemRequest request, User user) {
        validateDates(request.startDate(), request.endDate());
        PpcPlanningItem item = findAccessible(id, user);
        String title = request.title().trim();
        if (title.isEmpty()) throw badRequest("Title is required");
        item.setTitle(title); item.setDescription(clean(request.description())); item.setStartDate(request.startDate()); item.setEndDate(request.endDate());
        item.setPriority(clean(request.priority())); item.setStatus(clean(request.status()));
        return PpcPlanningItemResponse.from(repository.save(item));
    }

    @Transactional
    public void delete(Long id, User user) {
        repository.delete(findAccessible(id, user));
    }

    private Department resolveDepartment(Long departmentId, User user) {
        if (user == null || user.getDepartment() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Planning requires a department");
        if (departmentId == null || departmentId.equals(user.getDepartment().getId())) return user.getDepartment();
        if (!authorizationService.isAdmin(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only use your own department's planning calendar");
        return departmentRepository.findById(departmentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
    }

    // Items from another department are reported as missing so their existence is not revealed.
    private PpcPlanningItem findAccessible(Long id, User user) {
        PpcPlanningItem item = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planning item not found"));
        if (item.getDepartment() == null || !authorizationService.canAccessDepartment(user, item.getDepartment().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Planning item not found");
        }
        return item;
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw badRequest("End date must be on or after start date");
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
