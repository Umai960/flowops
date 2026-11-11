package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A machine looked after by the Maintenance department. */
@Entity
@Table(name = "machines", uniqueConstraints = @UniqueConstraint(name = "uk_machine_code", columnNames = "code"))
public class Machine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 60) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(length = 120) private String location;
    @Column(length = 120) private String model;
    @Column(length = 120) private String serialNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private MachineStatus status = MachineStatus.RUNNING;
    @Column(length = 2000) private String notes;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected Machine() { }
    public Machine(String code, String name) { this.code = code; this.name = name; }
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); if (updatedAt == null) updatedAt = createdAt; }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId(){return id;} public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;} public String getLocation(){return location;} public void setLocation(String v){location=v;}
    public String getModel(){return model;} public void setModel(String v){model=v;} public String getSerialNumber(){return serialNumber;} public void setSerialNumber(String v){serialNumber=v;}
    public MachineStatus getStatus(){return status;} public void setStatus(MachineStatus v){status=v;} public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
