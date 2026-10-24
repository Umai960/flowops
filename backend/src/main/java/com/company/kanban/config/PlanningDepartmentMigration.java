package com.company.kanban.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Planning used to be PPC-only, so items created before per-department calendars belong to the PPC calendar. */
@Component
@Profile({"dev", "prod"})
public class PlanningDepartmentMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public PlanningDepartmentMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.update("UPDATE ppc_planning_items SET department_id = " +
                "(SELECT id FROM departments WHERE UPPER(name) = 'PPC' ORDER BY id LIMIT 1) WHERE department_id IS NULL");
    }
}
