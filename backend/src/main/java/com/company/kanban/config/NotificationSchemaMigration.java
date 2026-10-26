package com.company.kanban.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Keeps the legacy PostgreSQL enum check in sync when new notification types are added. */
@Component
@Profile({"dev", "prod"})
public class NotificationSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public NotificationSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check");
        jdbcTemplate.execute("ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (" +
                "'TASK_CREATED','TASK_ASSIGNED','TASK_UPDATED','TASK_REASSIGNED'," +
                "'TASK_DUE_TOMORROW','TASK_DUE_TODAY','TASK_OVERDUE'," +
                "'TASK_REVIEW_SUBMITTED','TASK_REVIEW_RETURNED','TASK_APPROVED'," +
                "'PROJECT_CREATED','DAILY_REPORT_SUBMITTED'," +
                "'RAW_MATERIAL_ARRIVING_TOMORROW','RAW_MATERIAL_DUE_TODAY'," +
                "'RAW_MATERIAL_DELAYED','RAW_MATERIAL_FOLLOW_UP_DUE'," +
                "'MONTHLY_REPORT_3_DAY_REMINDER','MONTHLY_REPORT_MONTH_END','MONTHLY_REPORT_OVERDUE'," +
                "'REQUEST_CREATED','REQUEST_ACKNOWLEDGED','REQUEST_IN_PROGRESS','REQUEST_COMPLETED','REQUEST_REJECTED','REQUEST_ASSIGNED'," +
                "'CHAT_MESSAGE'))");
        // 1.3.0 created this enum check with TASK and PLANNING only; chat now also uses REQUEST.
        jdbcTemplate.execute("ALTER TABLE IF EXISTS chat_threads DROP CONSTRAINT IF EXISTS chat_threads_subject_type_check");
    }
}
