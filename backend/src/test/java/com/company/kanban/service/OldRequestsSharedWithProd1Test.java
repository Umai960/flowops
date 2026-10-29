package com.company.kanban.service;

import com.company.kanban.entity.*;
import com.company.kanban.repository.ChatThreadRepository;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.InterdepartmentRequestRepository;
import com.company.kanban.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Requests sent to PPC before 1.3.4, including finished ones, are shared with PROD_1 on start. */
@SpringBootTest
@ActiveProfiles("test")
class OldRequestsSharedWithProd1Test {
    @Autowired CommandLineRunner initializeData;
    @Autowired InterdepartmentRequestService requests;
    @Autowired InterdepartmentRequestRepository requestRepository;
    @Autowired ChatThreadRepository threads;
    @Autowired DepartmentRepository departments;
    @Autowired UserRepository users;

    @Test
    void oldPpcRequestsAndTheirChatsReachProd1() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Department rdd = departments.findByNameIgnoreCase("RDD").orElseThrow();
        Department ppc = departments.findByNameIgnoreCase("PPC").orElseThrow();
        Department prod1 = departments.findByNameIgnoreCase("PROD_1").orElseThrow();
        Department qc = departments.findByNameIgnoreCase("QC").orElseThrow();
        User requester = users.save(new User("Requester", "req." + s + "@co.test", "x", Role.STAFF, rdd));
        User prodStaff = users.save(new User("Prod Staff", "staff." + s + "@co.test", "x", Role.STAFF, prod1));

        InterdepartmentRequest open = requestRepository.save(new InterdepartmentRequest(rdd, ppc, requester, "Old open " + s, "details", Priority.MEDIUM, null));
        InterdepartmentRequest done = new InterdepartmentRequest(rdd, ppc, requester, "Old done " + s, "details", Priority.MEDIUM, null);
        done.setStatus(InterdepartmentRequestStatus.COMPLETED);
        done = requestRepository.save(done);
        InterdepartmentRequest toQc = requestRepository.save(new InterdepartmentRequest(rdd, qc, requester, "To QC " + s, "details", Priority.MEDIUM, null));
        ChatThread thread = threads.save(new ChatThread(ChatSubjectType.REQUEST, open.getId(), open.getTitle(), rdd, ppc));

        initializeData.run();

        assertThat(requests.list(prodStaff, "active", PageRequest.of(0, 100)).getContent())
                .extracting(r -> r.id()).contains(open.getId());
        assertThat(requests.list(prodStaff, "history", PageRequest.of(0, 100)).getContent())
                .extracting(r -> r.id()).contains(done.getId());
        assertThat(requestRepository.findById(toQc.getId()).orElseThrow().getCoTargetDepartment()).isNull();
        assertThat(threads.findById(thread.getId()).orElseThrow().getThirdDepartment().getId()).isEqualTo(prod1.getId());
    }
}
