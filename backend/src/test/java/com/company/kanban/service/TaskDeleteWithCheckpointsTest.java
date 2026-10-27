package com.company.kanban.service;
import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
@SpringBootTest @ActiveProfiles("test")
/** Deleting a task that has RDD checkpoints must also remove the checkpoints instead of failing. */
class TaskDeleteWithCheckpointsTest {
    @Autowired TaskService service; @Autowired TaskRepository tasks; @Autowired TaskCheckpointRepository cps; @Autowired UserRepository users; @Autowired DepartmentRepository departments;
    @Test void deletingTaskRemovesItsCheckpoints() {
        String s = UUID.randomUUID().toString().substring(0,6);
        Department d = departments.save(new Department("RDD probe " + s));
        User admin = users.save(new User("A", "a"+s+"@x.test", "x", Role.ADMIN, d));
        Task t = new Task("T", "d", Priority.MEDIUM, null, 1, null, admin);
        t.setDepartment(d);
        t = tasks.save(t);
        cps.save(new TaskCheckpoint(t, "cp", null, null, 1));
        service.deleteTask(t.getId(), admin);
        assertThat(tasks.findById(t.getId())).isEmpty();
        assertThat(cps.countByTaskId(t.getId())).isZero();
    }
}
