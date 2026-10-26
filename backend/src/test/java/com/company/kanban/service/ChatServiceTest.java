package com.company.kanban.service;

import com.company.kanban.dto.ChatDtos.*;
import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatServiceTest {
    @Autowired ChatService chat;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired InterdepartmentRequestRepository requests;
    @Autowired PpcPlanningItemRepository planningItems;
    @Autowired NotificationRepository notifications;
    @Autowired ChatThreadRepository threads;
    @Autowired ChatMessageRepository messages;

    User requester, requesterColleague, ppcManager, ppcStaff, outsider;
    InterdepartmentRequest request;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Department rdd = departments.save(new Department("Chat RDD " + suffix));
        Department ppc = departments.save(new Department("Chat PPC " + suffix));
        Department qa = departments.save(new Department("Chat QA " + suffix));
        requester = user("Aina", Role.STAFF, rdd, suffix);
        requesterColleague = user("Danial", Role.STAFF, rdd, suffix);
        ppcManager = user("Badrul", Role.MANAGER, ppc, suffix);
        ppcStaff = user("Siti", Role.STAFF, ppc, suffix);
        outsider = user("Chong", Role.STAFF, qa, suffix);
        request = requests.save(new InterdepartmentRequest(rdd, ppc, requester, "Sample resin for trial", "Need 5 kg", Priority.MEDIUM, LocalDate.of(2026, 11, 5)));
    }

    @Test
    void bothDepartmentsChatAndTheReceivingManagerIsNotified() {
        ChatMessageResponse sent = chat.post(ChatSubjectType.REQUEST, request.getId(), "  Can PPC send it by Thursday?  ", null, requester);

        assertThat(sent.body()).isEqualTo("Can PPC send it by Thursday?");
        ChatView view = chat.view(ChatSubjectType.REQUEST, request.getId(), null, ppcStaff);
        assertThat(view.departmentName()).contains("→");
        assertThat(view.messages()).extracting(ChatMessageResponse::body).containsExactly("Can PPC send it by Thursday?");
        assertThat(chat.view(ChatSubjectType.REQUEST, request.getId(), null, requesterColleague).messages()).hasSize(1);
        assertThat(notifications.findByRecipientIdAndClearedAtIsNull(ppcManager.getId()))
                .anySatisfy(n -> {
                    assertThat(n.getType()).isEqualTo(NotificationType.CHAT_MESSAGE);
                    assertThat(n.getDestination()).isEqualTo("/chat?type=request&id=" + request.getId());
                    assertThat(n.getMessage()).isEqualTo("Aina: Can PPC send it by Thursday?");
                });

        chat.post(ChatSubjectType.REQUEST, request.getId(), "Yes", null, ppcManager);
        assertThat(chat.unreadCount(requester)).isEqualTo(1);
        chat.markRead(ChatSubjectType.REQUEST, request.getId(), requester);
        assertThat(chat.unreadCount(requester)).isZero();
        assertThat(chat.threads("mine", requester)).extracting(ChatThreadSummary::title).containsExactly("Sample resin for trial");
    }

    @Test
    void repeatedMessagesDoNotFloodTheBell() {
        chat.post(ChatSubjectType.REQUEST, request.getId(), "one", null, ppcManager);
        chat.post(ChatSubjectType.REQUEST, request.getId(), "two", null, ppcManager);

        assertThat(notifications.findByRecipientIdAndClearedAtIsNull(requester.getId())).hasSize(1);
        assertThat(chat.unreadCount(requester)).isEqualTo(2);
    }

    @Test
    void otherDepartmentsNeedAnInvitation() {
        assertThatThrownBy(() -> chat.view(ChatSubjectType.REQUEST, request.getId(), null, outsider))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Chat not found");

        chat.addParticipant(ChatSubjectType.REQUEST, request.getId(), outsider.getId(), requester);
        chat.post(ChatSubjectType.REQUEST, request.getId(), "QA can check on Tuesday", null, outsider);

        assertThat(chat.view(ChatSubjectType.REQUEST, request.getId(), null, outsider).messages()).hasSize(1);
        assertThat(notifications.findByRecipientIdAndClearedAtIsNull(outsider.getId()))
                .anySatisfy(n -> assertThat(n.getTitle()).isEqualTo("Added to a chat"));
    }

    @Test
    void managersSeeTheirDepartmentsRequestChatsAndAdminsSeeAll() {
        chat.post(ChatSubjectType.REQUEST, request.getId(), "hello", null, requester);
        Department other = departments.save(new Department("Chat Other " + UUID.randomUUID().toString().substring(0, 8)));
        User admin = user("Admin", Role.ADMIN, other, UUID.randomUUID().toString().substring(0, 8));

        assertThat(chat.threads("all", ppcManager)).extracting(ChatThreadSummary::subjectId).contains(request.getId());
        assertThat(chat.view(ChatSubjectType.REQUEST, request.getId(), null, admin).messages()).hasSize(1);
        assertThat(chat.threads("all", admin)).extracting(ChatThreadSummary::subjectId).contains(request.getId());
    }

    @Test
    void tasksAndPlanningItemsNoLongerHaveChat() {
        PpcPlanningItem item = planningItems.save(new PpcPlanningItem("Plan", null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), null, null, ppcStaff));

        assertThatThrownBy(() -> chat.post(ChatSubjectType.PLANNING, item.getId(), "hi", null, ppcStaff))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Chat not found");
    }

    @Test
    void filesAreStoredAndUnsafeTypesAreRejected() {
        MockMultipartFile pdf = new MockMultipartFile("file", "C:\\fakepath\\Quotation.pdf", "application/octet-stream", new byte[]{1, 2, 3});
        ChatMessageResponse sent = chat.post(ChatSubjectType.REQUEST, request.getId(), null, pdf, requester);

        assertThat(sent.attachment().name()).isEqualTo("Quotation.pdf");
        assertThat(sent.attachment().contentType()).isEqualTo("application/pdf");
        assertThat(chat.attachment(sent.id(), ppcStaff).data()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> chat.attachment(sent.id(), outsider)).isInstanceOf(ResponseStatusException.class);

        MockMultipartFile exe = new MockMultipartFile("file", "setup.exe", "application/octet-stream", new byte[]{1});
        assertThatThrownBy(() -> chat.post(ChatSubjectType.REQUEST, request.getId(), null, exe, requester))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not allowed");
        assertThatThrownBy(() -> chat.post(ChatSubjectType.REQUEST, request.getId(), "   ", null, requester))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void onlyTheSenderOrAnAdminCanDeleteAMessage() {
        ChatMessageResponse sent = chat.post(ChatSubjectType.REQUEST, request.getId(), "typo", null, requester);

        assertThatThrownBy(() -> chat.deleteMessage(sent.id(), ppcStaff)).isInstanceOf(ResponseStatusException.class);
        chat.deleteMessage(sent.id(), requester);

        ChatMessageResponse shown = chat.view(ChatSubjectType.REQUEST, request.getId(), null, ppcStaff).messages().getFirst();
        assertThat(shown.deleted()).isTrue();
        assertThat(shown.body()).isNull();
    }

    @Test
    void retentionDeletesOldMessagesAndTheirChats() {
        chat.post(ChatSubjectType.REQUEST, request.getId(), "old news", null, requester);
        chat.purgeOlderThan(LocalDateTime.now().plusMinutes(1));

        assertThat(threads.findBySubjectTypeAndSubjectId(ChatSubjectType.REQUEST, request.getId())).isEmpty();
        assertThat(messages.count()).isZero();
    }

    @Test
    void fileNamesAreCleaned() {
        assertThat(ChatService.cleanFileName("../../etc/pass\"wd.txt")).isEqualTo("pass_wd.txt");
        assertThat(ChatService.cleanFileName(null)).isEqualTo("file");
    }

    private User user(String name, Role role, Department department, String suffix) {
        return users.save(new User(name, name.toLowerCase().replace(' ', '.') + "." + suffix + "@chat.test", "x", role, department));
    }
}
