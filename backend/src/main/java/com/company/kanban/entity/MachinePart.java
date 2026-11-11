package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A spare part fitted to a machine. The current part has no replacedOn date; fitting a new part
 * with the same name closes the old record, which stays as history.
 */
@Entity
@Table(name = "machine_parts", indexes = {
        @Index(name = "idx_machine_part_machine", columnList = "machine_id,installed_on"),
        @Index(name = "idx_machine_part_expiry", columnList = "expiry_date")
})
public class MachinePart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "machine_id", nullable = false) private Machine machine;
    @Column(nullable = false, length = 160) private String partName;
    @Column(length = 80) private String partNumber;
    @Column(name = "installed_on", nullable = false) private LocalDate installedOn;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    @Column(length = 1000) private String notes;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recorded_by_id", nullable = false) private User recordedBy;
    private LocalDate replacedOn;
    private LocalDate expiringNotifiedOn;
    private LocalDate expiredNotifiedOn;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected MachinePart() { }
    public MachinePart(Machine machine, String partName, LocalDate installedOn, User recordedBy) {
        this.machine = machine; this.partName = partName; this.installedOn = installedOn; this.recordedBy = recordedBy;
    }
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId(){return id;} public Machine getMachine(){return machine;}
    public String getPartName(){return partName;} public void setPartName(String v){partName=v;}
    public String getPartNumber(){return partNumber;} public void setPartNumber(String v){partNumber=v;}
    public LocalDate getInstalledOn(){return installedOn;} public void setInstalledOn(LocalDate v){installedOn=v;}
    public LocalDate getExpiryDate(){return expiryDate;} public void setExpiryDate(LocalDate v){expiryDate=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public User getRecordedBy(){return recordedBy;}
    public LocalDate getReplacedOn(){return replacedOn;} public void setReplacedOn(LocalDate v){replacedOn=v;}
    public LocalDate getExpiringNotifiedOn(){return expiringNotifiedOn;} public void setExpiringNotifiedOn(LocalDate v){expiringNotifiedOn=v;}
    public LocalDate getExpiredNotifiedOn(){return expiredNotifiedOn;} public void setExpiredNotifiedOn(LocalDate v){expiredNotifiedOn=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
