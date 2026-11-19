package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One breakdown of a machine, from the report until the repair is done. */
@Entity
@Table(name = "machine_breakdowns", indexes = @Index(name = "idx_breakdown_machine", columnList = "machine_id,reported_at"))
public class MachineBreakdown {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "machine_id", nullable = false) private Machine machine;
    @Column(nullable = false, length = 2000) private String problem;
    @Column(length = 2000) private String actionTaken;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private MachineBreakdownStatus status = MachineBreakdownStatus.OPEN;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reported_by_id", nullable = false) private User reportedBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "completed_by_id") private User completedBy;
    @Column(name = "reported_at", nullable = false) private LocalDateTime reportedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    protected MachineBreakdown() { }
    public MachineBreakdown(Machine machine, String problem, User reportedBy, LocalDateTime reportedAt) {
        this.machine = machine; this.problem = problem; this.reportedBy = reportedBy; this.reportedAt = reportedAt;
    }

    public Long getId(){return id;} public Machine getMachine(){return machine;} public String getProblem(){return problem;} public void setProblem(String v){problem=v;}
    public String getActionTaken(){return actionTaken;} public void setActionTaken(String v){actionTaken=v;}
    public MachineBreakdownStatus getStatus(){return status;} public void setStatus(MachineBreakdownStatus v){status=v;}
    public User getReportedBy(){return reportedBy;} public User getCompletedBy(){return completedBy;} public void setCompletedBy(User v){completedBy=v;}
    public LocalDateTime getReportedAt(){return reportedAt;} public LocalDateTime getStartedAt(){return startedAt;} public void setStartedAt(LocalDateTime v){startedAt=v;}
    public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
}
