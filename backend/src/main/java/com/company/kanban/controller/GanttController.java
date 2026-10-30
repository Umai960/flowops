package com.company.kanban.controller;

import com.company.kanban.dto.GanttProjectResponse;
import com.company.kanban.entity.User;
import com.company.kanban.service.GanttService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gantt")
public class GanttController {
    private final GanttService service;
    public GanttController(GanttService service) { this.service = service; }

    @GetMapping
    public List<GanttProjectResponse> projects(@RequestParam(required = false) Long departmentId, @AuthenticationPrincipal User user) {
        return service.getProjects(departmentId, user);
    }
}
