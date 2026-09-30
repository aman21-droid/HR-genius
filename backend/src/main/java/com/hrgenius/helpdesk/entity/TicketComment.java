package com.hrgenius.helpdesk.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** A reply on a ticket; internal notes are visible to agents only. */
@Getter
@Setter
@Entity
@Table(name = "helpdesk_comments")
@SQLRestriction("deleted = 0")
public class TicketComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ticket_comment_seq_gen")
    @SequenceGenerator(name = "ticket_comment_seq_gen", sequenceName = "ticket_comment_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_emp_id", nullable = false, updatable = false)
    private Employee author;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "internal_note", nullable = false)
    private boolean internalNote = false;
}
