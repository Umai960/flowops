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
    @Autowired PpcPlanningService planning;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired PpcPlanningItemRepository planningItems;
    @Autowired NotificationRepository notifications;
    @Autowired ChatThreadRepository threads;
    @Autowired ChatMessageRepository messages;

    User creator, colleague, outsider, admin;
    PpcPlanningItem item;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Department ppc = departments.save(new Department("Chat PPC " + suffix));
        Department qa = departments.save(new Department("Chat QA " + suffix));
        creator = user("Aina", Role.STAFF, ppc, suffix);
        colleague = user("Badrul", Role.STAFF, ppc, suffix);
        outsider = user("Chong", Role.STAFF, qa, suffix);
        admin = user("Admin", Role.ADMIN, ppc, suffix);
        item = planningItems.save(new PpcPlanningItem("Shutdown plan", null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), null, null, creator));
    }

    @Test
    void departmentMembersChatAndTheCreatorIsNotified() {
        ChatMessageResponse sent = chat.post(ChatSubjectType.PLANNING, item.getId(), "  Material arrives Monday  ", null, colleague);

        assertThat(sent.body()).isEqualTo("Material arrives Monday");
        assertThat(sent.senderName()).isEqualTo("Badrul");
        ChatView view = chat.view(ChatSubjectType.PLANNING, item.getId(), null, creator);
        assertThat(view.messages()).extracting(ChatMessageResponse::body).containsExactly("Material arrives Monday");
        assertThat(view.participants()).extracting(ChatPerson::name).containsExactlyInAnyOrder("Aina", "Badrul");
        assertThat(notifications.findByRecipientIdAndClearedAtIsNull(creator.getId()))
                .anySatisfy(n -> {
                    assertThat(n.getType()).isEqualTo(NotificationType.CHAT_MESSAGE);
                    assertThat(n.getDestination()).isEqualTo("/chat?type=planning&id=" + item.getId());
                    assertThat(n.getMessage()).isEqualTo("Badrul: Material arrives Monday");
                });
        assertThat(notifications.findByRecipientIdAndClearedAtIsNull(colleague.getId())).isEmpty();
        assertThat(chat.unreadCount(creator)).isEqualTo(1);
        chat.markRead(ChatSubjectType.PLANNING, item.getId(), creator);
        assertThat(chat.unreadCount(creator)).isZero();
    }

    @Test
    void repeatedMessagesDoNotFloodTheBell() {
        chat.post(ChatSubjectType.PLANNING, item.getId(), "one", null, colleague);
        chat.post(ChatSubjectType.PLANNING, item.getId(), "two", null, colleague);

        assertThat(notifications.findByRecipientIdAndClearedAtIsNull(creator.getId())).hasSize(1);
        assertThat(chat.unreadCount(creator)).isEqualTo(2);
    }

    @Test
    void otherDepartmentsNeedAnInvitationAndThenSeeOnlyThatChat() {
        assertThatThrownBy(() -> chat.view(ChatSubjectType.PLANNING, item.getId(), null, outsider))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Chat not found");

        chat.addParticipant(ChatSubjectType.PLANNING, item.getId(), outsider.getId(), creator);
        chat.post(ChatSubjectType.PLANNING, item.getId(), "QA can check on Tuesday", null, outsider);

        assertThat(chat.view(ChatSubjectType.PLANNING, item.getId(), null, outsider).messages()).hasSize(1);
        assertThat(chat.threads("mine", outsider)).extracting(ChatThreadSummary::title).containsExactly("Shutdown plan");
        assertThat(notifications.findByRecipientIdAndClearedAtIsNull(outsider.getId()))
                .anySatisfy(n -> assertThat(n.getTitle()).isEqualTo("Added to a chat"));
        assertThatThrownBy(() -> planning.update(item.getId(), null, outsider)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void adminsCanReadEveryChat() {
        chat.post(ChatSubjectType.PLANNING, item.getId(), "hello", null, creator);
        Department other = departments.save(new Department("Chat Other " + UUID.randomUUID().toString().substring(0, 8)));
        User otherAdmin = user("Other Admin", Role.ADMIN, other, UUID.randomUUID().toString().substring(0, 8));

        assertThat(chat.view(ChatSubjectType.PLANNING, item.getId(), null, otherAdmin).messages()).hasSize(1);
        assertThat(chat.threads("all", otherAdmin)).extracting(ChatThreadSummary::subjectId).contains(item.getId());
    }

    @Test
    void filesAreStoredAndUnsafeTypesAreRejected() {
        MockMultipartFile pdf = new MockMultipartFile("file", "C:\\fakepath\\Quotation.pdf", "application/octet-stream", new byte[]{1, 2, 3});
        ChatMessageResponse sent = chat.post(ChatSubjectType.PLANNING, item.getId(), null, pdf, creator);

        assertThat(sent.attachment().name()).isEqualTo("Quotation.pdf");
        assertThat(sent.attachment().contentType()).isEqualTo("application/pdf");
        ChatService.AttachmentDownload download = chat.attachment(sent.id(), colleague);
        assertThat(download.data()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> chat.attachment(sent.id(), outsider)).isInstanceOf(ResponseStatusException.class);

        MockMultipartFile exe = new MockMultipartFile("file", "setup.exe", "application/octet-stream", new byte[]{1});
        assertThatThrownBy(() -> chat.post(ChatSubjectType.PLANNING, item.getId(), null, exe, creator))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not allowed");
        assertThatThrownBy(() -> chat.post(ChatSubjectType.PLANNING, item.getId(), "   ", null, creator))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void onlyTheSenderOrAnAdminCanDeleteAMessage() {
        ChatMessageResponse sent = chat.post(ChatSubjectType.PLANNING, item.getId(), "typo", null, creator);

        assertThatThrownBy(() -> chat.deleteMessage(sent.id(), colleague)).isInstanceOf(ResponseStatusException.class);
        chat.deleteMessage(sent.id(), creator);

        ChatMessageResponse shown = chat.view(ChatSubjectType.PLANNING, item.getId(), null, colleague).messages().getFirst();
        assertThat(shown.deleted()).isTrue();
        assertThat(shown.body()).isNull();
    }

    @Test
    void retentionDeletesOldMessagesAndDeletedItemsLoseTheirChat() {
        chat.post(ChatSubjectType.PLANNING, item.getId(), "old news", null, creator);
        chat.purgeOlderThan(LocalDateTime.now().plusMinutes(1));
        assertThat(threads.findBySubjectTypeAndSubjectId(ChatSubjectType.PLANNING, item.getId())).isEmpty();
        assertThat(messages.count()).isZero();

        chat.post(ChatSubjectType.PLANNING, item.getId(), "again", null, creator);
        planning.delete(item.getId(), creator);
        assertThat(threads.findBySubjectTypeAndSubjectId(ChatSubjectType.PLANNING, item.getId())).isEmpty();
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
