package com.company.kanban.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("prod")
public class SpaFallbackController {
    @GetMapping({
            "/",
            "/login",
            "/dashboard",
            "/projects",
            "/reviews",
            "/reports",
            "/reports/monthly",
            "/history",
            "/completed",
            "/admin",
            "/admin/users",
            "/admin/settings/data-management",
            "/admin/settings/client-access",
            "/settings/desktop-notifications",
            "/ppc/planning",
            "/chat",
            "/ppc/raw-material-arrivals",
            "/activate",
            "/manager",
            "/staff"
    })
    public String index() {
        return "forward:/index.html";
    }
}
