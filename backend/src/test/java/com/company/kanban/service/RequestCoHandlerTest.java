package com.company.kanban.service;

import com.company.kanban.dto.CreateInterdepartmentRequest;
import com.company.kanban.dto.InterdepartmentRequestResponse;
import com.company.kanban.dto.UpdateInterdepartmentRequestStatus;
import com.company.kanban.entity.*;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.NotificationRepository;
import com.company.kanban.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RDD requests reach PPC and PROD_1 together, and either department can handle them. */
@SpringBootTest
@ActiveProfiles("test")
class RequestCoHandlerTest {
    @Autowired InterdepartmentRequestService requests;
    @Autowired ChatService chat;
    @Autowired DepartmentRepository departments;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    @Test
    void prod1SeesHandlesAndChatsOnRddRequests() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Department rdd = departments.findByNameIgnoreCase("RDD").orElseThrow();
        Department prod1 = departments.findByNameIgnoreCase("PROD_1").orElseThrow();
        Department qc = departments.findByNameIgnoreCase("QC").orElseThrow();
        User requester = users.save(new User("Requester", "req." + s + "@co.test", "x", Role.STAFF, rdd));
        User prodHod = users.save(new User("Prod HOD", "hod." + s + "@co.test", "x", Role.MANAGER, prod1));
        User prodStaff = users.save(new User("Prod Staff", "staff." + s + "@co.test", "x", Role.STAFF, prod1));
        User outsider = users.save(new User("Outsider", "qc." + s + "@co.test", "x", Role.STAFF, qc));

        InterdepartmentRequestResponse created = requests.create(
                new CreateInterdepartmentRequest("Run test lot " + s, "details", Priority.HIGH, null, null), requester);
        assertThat(created.targetDepartment()).isEqualTo("PPC");
        assertThat(created.coTargetDepartment()).isEqualTo("PROD_1");
        assertThat(notifications.findByRecipientIdAndClearedAtIsNull(prodHod.getId()))
                .anyMatch(n -> n.getType() == NotificationType.REQUEST_CREATED);

        assertThat(requests.list(prodStaff, "active", PageRequest.of(0, 100)).getContent())
                .extracting(InterdepartmentRequestResponse::id).contains(created.id());
        assertThat(requests.list(outsider, "active", PageRequest.of(0, 100)).getContent())
                .extracting(InterdepartmentRequestResponse::id).doesNotContain(created.id());

        InterdepartmentRequestResponse acknowledged = requests.update(created.id(),
                new UpdateInterdepartmentRequestStatus(InterdepartmentRequestStatus.ACKNOWLEDGED, null, null), prodHod);
        assertThat(acknowledged.status()).isEqualTo(InterdepartmentRequestStatus.ACKNOWLEDGED);

        chat.post(ChatSubjectType.REQUEST, created.id(), "On it", null, prodStaff);
        assertThat(chat.view(ChatSubjectType.REQUEST, created.id(), null, requester).departmentName()).isEqualTo("RDD → PPC, PROD_1");
        assertThatThrownBy(() -> chat.view(ChatSubjectType.REQUEST, created.id(), null, outsider)).isInstanceOf(ResponseStatusException.class);
    }
}
