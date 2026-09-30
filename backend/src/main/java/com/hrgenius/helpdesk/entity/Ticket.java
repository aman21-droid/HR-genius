package com.hrgenius.helpdesk.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** An employee support request routed to the helpdesk agents. */
@Getter
@Setter
@Entity
@Table(name = "helpdesk_tickets")
@SQLRestriction("deleted = 0")
public class Ticket extends BaseEntity {

    public enum Category { IT, HR, PAYROLL, FACILITIES, OTHER }

    /** Priority drives the resolution deadline. */
    public enum Priority {
        LOW(Duration.ofDays(5)), MEDIUM(Duration.ofDays(3)), HIGH(Duration.ofDays(1)), URGENT(Duration.ofHours(4));

        public final Duration sla;

        Priority(Duration sla) {
            this.sla = sla;
        }
    }

    public enum Status { OPEN, IN_PROGRESS, RESOLVED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ticket_seq_gen")
    @SequenceGenerator(name = "ticket_seq_gen", sequenceName = "ticket_seq", allocationSize = 1)
    private Long id;

    @Column(name = "ticket_no", nullable = false, length = 20, updatable = false)
    private String ticketNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_emp_id", nullable = false, updatable = false)
    private Employee requester;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "description", nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_emp_id")
    private Employee assignee;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "resolution_note", length = 2000)
    private String resolutionNote;

    @Column(name = "satisfaction")
    private Integer satisfaction;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<TicketComment> comments = new ArrayList<>();

    public void addComment(TicketComment c) {
        c.setTicket(this);
        comments.add(c);
    }

    public boolean isOverdue() {
        return (status == Status.OPEN || status == Status.IN_PROGRESS) && dueAt.isBefore(Instant.now());
    }
}
