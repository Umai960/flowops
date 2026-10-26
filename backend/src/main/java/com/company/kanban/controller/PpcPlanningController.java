package com.company.kanban.controller;

import com.company.kanban.dto.CreatePpcPlanningItemRequest;
import com.company.kanban.dto.PpcPlanningItemResponse;
import com.company.kanban.dto.UpdatePpcPlanningItemRequest;
import com.company.kanban.entity.User;
import com.company.kanban.service.PpcPlanningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ppc/planning")
public class PpcPlanningController {
    private final PpcPlanningService service;
    public PpcPlanningController(PpcPlanningService service) { this.service = service; }

    @GetMapping
    public List<PpcPlanningItemResponse> get(@RequestParam int year, @RequestParam int month, @RequestParam(required = false) Long departmentId,
                                             @AuthenticationPrincipal User user) {
        return service.getItems(year, month, departmentId, user);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PpcPlanningItemResponse create(@Valid @RequestBody CreatePpcPlanningItemRequest request, @AuthenticationPrincipal User user) { return service.create(request, user); }
    @PutMapping("/{id}")
    public PpcPlanningItemResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePpcPlanningItemRequest request, @AuthenticationPrincipal User user) { return service.update(id, request, user); }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) { service.delete(id, user); }
}
