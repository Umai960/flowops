package com.company.kanban.config;

import com.company.kanban.entity.Board;
import com.company.kanban.entity.Department;
import com.company.kanban.entity.KanbanColumn;
import com.company.kanban.repository.BoardRepository;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.KanbanColumnRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@Profile("!demo")
public class DataInitializer {

    private static final String PPC_DEPARTMENT_NAME = "PPC";
    private static final String PROD_1_DEPARTMENT_NAME = "PROD_1";
    private static final String DEFAULT_BOARD_NAME = "PPC Workflow Board";
    private static final String DEFAULT_BOARD_DESCRIPTION =
        "Workflow board for the PPC team";
    private static final List<String> DEFAULT_COLUMNS = List.of(
        "To Do",
        "In Progress",
        "Review",
        "Done"
    );

    @Bean
    CommandLineRunner initializeData(
            DepartmentRepository departmentRepository,
            BoardRepository boardRepository,
            KanbanColumnRepository kanbanColumnRepository,
            JdbcTemplate jdbcTemplate,
            Environment environment) {

        return args -> {

            // Existing installations were created with column_id NOT NULL before
            // general tasks were introduced. Hibernate's update mode does not
            // reliably relax that constraint, so make the additive migration
            // explicit before the first general-task insert.
            if (!List.of(environment.getActiveProfiles()).contains("test")) {
                jdbcTemplate.execute("ALTER TABLE IF EXISTS tasks ALTER COLUMN column_id DROP NOT NULL");
                jdbcTemplate.execute("ALTER TABLE IF EXISTS task_snapshots ALTER COLUMN board_id DROP NOT NULL");
                jdbcTemplate.execute("ALTER TABLE IF EXISTS task_snapshots ALTER COLUMN board_name DROP NOT NULL");
            }

            String[] departments = {
                "PPC",
                "PROD",
                "PROD_1",
                "RDD",
                "QC",
                "Maintenance"
            };

            for (String name : departments) {

                if (departmentRepository.findByNameIgnoreCase(name).isEmpty()) {
                    departmentRepository.save(
                        new Department(name)
                    );
                }
            }

            Department ppcDepartment = departmentRepository
                    .findByNameIgnoreCase(PPC_DEPARTMENT_NAME)
                    .orElseThrow(() -> new IllegalStateException(
                            "PPC department is missing"
                    ));

            // Requests sent to PPC before PROD_1 became a co-handler (1.3.4) are
            // shared with PROD_1 too, together with their chat threads. Only
            // rows still missing a co-handler are touched, so this is safe to
            // run on every start.
            departmentRepository.findByNameIgnoreCase(PROD_1_DEPARTMENT_NAME).ifPresent(prod1 -> {
                jdbcTemplate.update(
                    "UPDATE interdepartment_requests SET co_target_department_id = ? "
                        + "WHERE target_department_id = ? AND co_target_department_id IS NULL",
                    prod1.getId(), ppcDepartment.getId());
                jdbcTemplate.update(
                    "UPDATE chat_threads SET third_department_id = ? "
                        + "WHERE subject_type = 'REQUEST' AND third_department_id IS NULL "
                        + "AND subject_id IN (SELECT id FROM interdepartment_requests "
                        + "WHERE co_target_department_id = ?)",
                    prod1.getId(), prod1.getId());
            });

            Board board = boardRepository
                    .findByNameIgnoreCaseAndDepartmentId(
                            DEFAULT_BOARD_NAME,
                            ppcDepartment.getId()
                    )
                    .orElseGet(() -> boardRepository.save(
                            new Board(
                                    DEFAULT_BOARD_NAME,
                                    DEFAULT_BOARD_DESCRIPTION,
                                    ppcDepartment
                            )
                    ));

            Set<String> existingColumns = kanbanColumnRepository
                    .findByBoardIdOrderByPositionAsc(board.getId())
                    .stream()
                    .map(column -> column.getName().toLowerCase())
                    .collect(Collectors.toSet());

            for (int index = 0; index < DEFAULT_COLUMNS.size(); index++) {
                String columnName = DEFAULT_COLUMNS.get(index);

                if (!existingColumns.contains(columnName.toLowerCase())) {
                    kanbanColumnRepository.save(
                            new KanbanColumn(
                                    columnName,
                                    index + 1,
                                    board
                            )
                    );
                }
                    }
        };
    }
}
