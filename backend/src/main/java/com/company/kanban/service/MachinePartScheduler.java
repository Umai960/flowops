package com.company.kanban.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily spare-part expiry check, also run once at startup so a missed morning is caught up. */
@Component
public class MachinePartScheduler {
    private final MachineService machines;

    public MachinePartScheduler(MachineService machines) { this.machines = machines; }

    @Scheduled(cron = "0 5 8 * * *", zone = "Asia/Kuala_Lumpur") public void dailyCheck() { machines.notifyPartExpiry(); }
    @EventListener(ApplicationReadyEvent.class) public void recoverOnStartup() { machines.notifyPartExpiry(); }
}
