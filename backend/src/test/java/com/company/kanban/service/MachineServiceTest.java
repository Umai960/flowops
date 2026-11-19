package com.company.kanban.service;

import com.company.kanban.dto.MachineBreakdownRequest;
import com.company.kanban.dto.MachineBreakdownUpdateRequest;
import com.company.kanban.dto.MachinePartRequest;
import com.company.kanban.dto.MachineRequest;
import com.company.kanban.entity.*;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.NotificationRepository;
import com.company.kanban.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Machines belong to Maintenance; HODs manage the list and breakdowns drive the machine status. */
@SpringBootTest
@ActiveProfiles("test")
class MachineServiceTest {
    @Autowired MachineService machines;
    @Autowired DepartmentRepository departments;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    @Test
    void onlyMaintenanceSeesMachinesAndOnlyItsHodManagesThem() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Department maintenance = departments.findByNameIgnoreCase("Maintenance").orElseThrow();
        Department ppc = departments.findByNameIgnoreCase("PPC").orElseThrow();
        User hod = users.save(new User("Mt HOD", "mhod." + s + "@co.test", "x", Role.MANAGER, maintenance));
        User staff = users.save(new User("Mt Staff", "mstaff." + s + "@co.test", "x", Role.STAFF, maintenance));
        User ppcHod = users.save(new User("PPC HOD", "phod." + s + "@co.test", "x", Role.MANAGER, ppc));

        MachineRequest request = new MachineRequest("M-" + s, "Press", "Line 1", null, null, null, null, null);
        assertThatThrownBy(() -> machines.create(request, staff)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> machines.list(ppcHod)).isInstanceOf(ResponseStatusException.class);
        assertThat(machines.create(request, hod).status()).isEqualTo(MachineStatus.RUNNING);
        assertThatThrownBy(() -> machines.create(request, hod)).isInstanceOf(ResponseStatusException.class);
        assertThat(machines.list(staff)).anyMatch(m -> m.code().equals("M-" + s));
    }

    @Test
    void csvImportCreatesUpdatesAndReportsBadRows() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Department maintenance = departments.findByNameIgnoreCase("Maintenance").orElseThrow();
        User hod = users.save(new User("Mt HOD", "ihod." + s + "@co.test", "x", Role.MANAGER, maintenance));
        String csv = "﻿Kod Mesin;Nama Mesin;Lokasi;Status;Catatan\r\n"
                + "A-" + s + ";\"Lathe; big\";Line 2;Idle;\"said \"\"old\"\"\"\r\n"
                + "B-" + s + ";Drill;;;\r\n"
                + ";No code;;;\r\n"
                + "C-" + s + ";Mill;;Exploded;\r\n";
        var first = machines.importCsv(csv, hod);
        assertThat(first.created()).isEqualTo(2);
        assertThat(first.errors()).hasSize(2);
        var lathe = machines.list(hod).stream().filter(m -> m.code().equals("A-" + s)).findFirst().orElseThrow();
        assertThat(lathe.name()).isEqualTo("Lathe; big");
        assertThat(lathe.status()).isEqualTo(MachineStatus.IDLE);
        assertThat(lathe.notes()).isEqualTo("said \"old\"");

        var second = machines.importCsv("Code,Name\nA-" + s + ",Lathe 2\n", hod);
        assertThat(second.updated()).isEqualTo(1);
        assertThat(machines.list(hod).stream().filter(m -> m.code().equals("A-" + s)).findFirst().orElseThrow().location()).isEqualTo("Line 2");
    }

    @Test
    void breakdownMovesMachineThroughRepairAndNotifiesHod() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Department maintenance = departments.findByNameIgnoreCase("Maintenance").orElseThrow();
        User hod = users.save(new User("Mt HOD", "bhod." + s + "@co.test", "x", Role.MANAGER, maintenance));
        User staff = users.save(new User("Mt Staff", "bstaff." + s + "@co.test", "x", Role.STAFF, maintenance));
        Long id = machines.create(new MachineRequest("X-" + s, "Mixer", null, null, null, null, null, null), hod).id();

        var breakdown = machines.report(id, new MachineBreakdownRequest("Motor noise"), staff);
        assertThat(status(id, staff)).isEqualTo(MachineStatus.BREAKDOWN);
        assertThat(notifications.findByRecipientIdAndClearedAtIsNull(hod.getId()))
                .anyMatch(n -> n.getType() == NotificationType.MACHINE_BREAKDOWN);

        machines.updateBreakdown(breakdown.id(), new MachineBreakdownUpdateRequest(MachineBreakdownStatus.IN_PROGRESS, null), staff);
        assertThat(status(id, staff)).isEqualTo(MachineStatus.UNDER_MAINTENANCE);
        var done = machines.updateBreakdown(breakdown.id(), new MachineBreakdownUpdateRequest(MachineBreakdownStatus.COMPLETED, "Replaced bearing"), staff);
        assertThat(done.completedByName()).isEqualTo("Mt Staff");
        assertThat(done.actionTaken()).isEqualTo("Replaced bearing");
        assertThat(status(id, staff)).isEqualTo(MachineStatus.RUNNING);
        assertThat(machines.machineHistory(id, staff)).hasSize(1);
        assertThatThrownBy(() -> machines.updateBreakdown(breakdown.id(), new MachineBreakdownUpdateRequest(MachineBreakdownStatus.OPEN, null), staff))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sparePartsKeepHistoryShowExpiryAndWarnTheHodOnce() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Department maintenance = departments.findByNameIgnoreCase("Maintenance").orElseThrow();
        User hod = users.save(new User("Mt HOD", "phod." + s + "@co.test", "x", Role.MANAGER, maintenance));
        User staff = users.save(new User("Mt Staff", "pstaff." + s + "@co.test", "x", Role.STAFF, maintenance));
        Long id = machines.create(new MachineRequest("P-" + s, "Pump", null, null, null, null, null, null), hod).id();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur"));

        var oldBelt = machines.recordPart(id, new MachinePartRequest("Belt " + s, null, today.minusDays(200), today.minusDays(5), null), staff);
        assertThat(oldBelt.expiryStatus()).isEqualTo("EXPIRED");
        var filter = machines.recordPart(id, new MachinePartRequest("Filter " + s, "F-1", today.minusDays(10), today.plusDays(10), null), staff);
        assertThat(filter.expiryStatus()).isEqualTo("EXPIRING");
        assertThat(filter.daysLeft()).isEqualTo(10L);
        assertThatThrownBy(() -> machines.recordPart(id, new MachinePartRequest("Bad " + s, null, today, today.minusDays(1), null), staff))
                .isInstanceOf(ResponseStatusException.class);

        machines.notifyPartExpiry();
        machines.notifyPartExpiry();
        var hodNotes = notifications.findByRecipientIdAndClearedAtIsNull(hod.getId());
        assertThat(hodNotes.stream().filter(n -> n.getType() == NotificationType.SPARE_PART_EXPIRED && n.getMessage().contains("Belt " + s))).hasSize(1);
        assertThat(hodNotes.stream().filter(n -> n.getType() == NotificationType.SPARE_PART_EXPIRING && n.getMessage().contains("Filter " + s))).hasSize(1);

        var newBelt = machines.recordPart(id, new MachinePartRequest("belt " + s, null, today, today.plusDays(365), "New supplier"), staff);
        assertThat(newBelt.expiryStatus()).isEqualTo("OK");
        var history = machines.machineParts(id, staff);
        assertThat(history).hasSize(3);
        assertThat(history.stream().filter(p -> p.id().equals(oldBelt.id())).findFirst().orElseThrow().replacedOn()).isEqualTo(today);
        assertThat(machines.currentParts(staff)).extracting(p -> p.id()).contains(newBelt.id(), filter.id()).doesNotContain(oldBelt.id());

        assertThatThrownBy(() -> machines.deletePart(filter.id(), staff)).isInstanceOf(ResponseStatusException.class);
        machines.deletePart(filter.id(), hod);
        assertThat(machines.machineParts(id, staff)).hasSize(2);
    }

    private MachineStatus status(Long id, User user) {
        return machines.list(user).stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow().status();
    }
}
