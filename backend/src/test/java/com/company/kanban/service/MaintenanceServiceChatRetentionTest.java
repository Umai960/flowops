package com.company.kanban.service;

import com.company.kanban.repository.ActivationTokenRepository;
import com.company.kanban.repository.DeviceRegistrationCodeRepository;
import com.company.kanban.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Chat is kept forever unless a retention period is configured. */
class MaintenanceServiceChatRetentionTest {
    private final ChatService chat = mock(ChatService.class);

    private MaintenanceService service(int chatDays) {
        return new MaintenanceService(mock(NotificationRepository.class), mock(ActivationTokenRepository.class),
                mock(DeviceRegistrationCodeRepository.class), mock(DataManagementService.class), 90, chat, chatDays);
    }

    @Test
    void zeroDaysKeepsChatForever() {
        service(0).run();
        verify(chat, never()).purgeOlderThan(any());
    }

    @Test
    void aConfiguredPeriodStillPurges() {
        service(90).run();
        verify(chat).purgeOlderThan(any());
    }
}
