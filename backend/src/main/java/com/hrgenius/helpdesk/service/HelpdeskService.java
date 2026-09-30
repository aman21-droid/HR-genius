package com.hrgenius.helpdesk.service;

import com.hrgenius.auth.repository.UserRepository;
import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.common.util.SearchPredicates;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.helpdesk.dto.HelpdeskDtos.*;
import com.hrgenius.helpdesk.entity.Ticket;
import com.hrgenius.helpdesk.entity.Ticket.Category;
import com.hrgenius.helpdesk.entity.Ticket.Priority;
import com.hrgenius.helpdesk.entity.Ticket.Status;
import com.hrgenius.helpdesk.entity.TicketComment;
import com.hrgenius.helpdesk.repository.TicketRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Helpdesk tickets. Requesters see their own tickets and non-internal replies; agents
 * (HELPDESK_AGENT) see the whole queue, triage, reply, add internal notes and resolve.
 * A resolved ticket can be reopened or closed (with a satisfaction score) by its requester.
 */
@Service
public class HelpdeskService {

    public static final String AGENT_PERMISSION = "HELPDESK_AGENT";

    private final TicketRepository tickets;
    private final EmployeeRepository employees;
    private final UserRepository users;
    private final CurrentUserService currentUser;

    public HelpdeskService(TicketRepository tickets, EmployeeRepository employees, UserRepository users,
                           CurrentUserService currentUser) {
        this.tickets = tickets;
        this.employees = employees;
        this.users = users;
        this.currentUser = currentUser;
    }

    // ---------------------------------------------------------------- requester

    @Transactional
    public TicketDto create(Long requesterEmpId, CreateTicketRequest req) {
        Ticket t = new Ticket();
        t.setTicketNo("HD-" + String.format("%05d", tickets.nextTicketNumber()));
        t.setRequester(employees.getReferenceById(requesterEmpId));
        t.setCategory(req.category());
        t.setPriority(req.priority() == null ? Priority.MEDIUM : req.priority());
        t.setSubject(req.subject().trim());
        t.setDescription(req.description().trim());
        t.setStatus(Status.OPEN);
        t.setDueAt(Instant.now().plus(t.getPriority().sla));
        tickets.save(t);
        return toDto(tickets.findById(t.getId()).orElseThrow(), requesterEmpId);
    }

    @Transactional(readOnly = true)
    public List<TicketSummaryDto> mine(Long employeeId) {
        return tickets.findForRequester(employeeId).stream().map(t -> toSummary(t, false)).toList();
    }

    // ---------------------------------------------------------------- shared

    @Transactional(readOnly = true)
    public TicketDto get(Long id) {
        Long me = me();
        Ticket t = findVisible(id, me);
        return toDto(t, me);
    }

    @Transactional
    public TicketDto comment(Long id, CommentRequest req) {
        Long me = me();
        Ticket t = findVisible(id, me);
        boolean agent = isAgent();
        boolean requester = t.getRequester().getId().equals(me);
        boolean internal = Boolean.TRUE.equals(req.internal());
        if (internal && !agent) {
            throw new AccessDeniedException("Only agents can add internal notes");
        }
        if (t.getStatus() == Status.CLOSED) {
            throw new BusinessException("This ticket is closed; raise a new one if you still need help");
        }
        TicketComment c = new TicketComment();
        c.setAuthor(employees.getReferenceById(me));
        c.setBody(req.body().trim());
        c.setInternalNote(internal);
        t.addComment(c);
        if (agent && !requester && !internal) {
            if (t.getFirstResponseAt() == null) {
                t.setFirstResponseAt(Instant.now());
            }
            if (t.getStatus() == Status.OPEN) {
                t.setStatus(Status.IN_PROGRESS);
            }
        } else if (requester && t.getStatus() == Status.RESOLVED) {
            // A reply from the requester on a resolved ticket reopens it.
            t.setStatus(Status.IN_PROGRESS);
            t.setResolvedAt(null);
        }
        tickets.save(t);
        return toDto(t, me);
    }

    /** Requester confirms the fix (optionally rating it) or closes their own open ticket. */
    @Transactional
    public TicketDto close(Long id, CloseRequest req) {
        Long me = me();
        Ticket t = findVisible(id, me);
        if (!t.getRequester().getId().equals(me) && !isAgent()) {
            throw new AccessDeniedException("Not allowed to close this ticket");
        }
        if (t.getStatus() == Status.CLOSED) {
            throw new BusinessException("This ticket is already closed");
        }
        t.setStatus(Status.CLOSED);
        t.setClosedAt(Instant.now());
        if (req != null && req.satisfaction() != null && t.getRequester().getId().equals(me)) {
            t.setSatisfaction(req.satisfaction());
        }
        return toDto(t, me);
    }

    @Transactional
    public TicketDto reopen(Long id) {
        Long me = me();
        Ticket t = findVisible(id, me);
        if (!t.getRequester().getId().equals(me)) {
            throw new AccessDeniedException("Only the requester can reopen a ticket");
        }
        if (t.getStatus() != Status.RESOLVED) {
            throw new BusinessException("Only a resolved ticket can be reopened");
        }
        t.setStatus(Status.IN_PROGRESS);
        t.setResolvedAt(null);
        return toDto(t, me);
    }

    // ---------------------------------------------------------------- agents

    @Transactional(readOnly = true)
    public PageResponse<TicketSummaryDto> queue(String search, Status status, Category category, Boolean mineOnly,
                                                Boolean overdueOnly, Pageable pageable) {
        Long me = me();
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                p.add(cb.or(SearchPredicates.containsIgnoreCase(cb, root.get("subject"), search),
                        SearchPredicates.containsIgnoreCase(cb, root.get("ticketNo"), search)));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (category != null) {
                p.add(cb.equal(root.get("category"), category));
            }
            if (Boolean.TRUE.equals(mineOnly)) {
                p.add(cb.equal(root.get("assignee").get("id"), me));
            }
            if (Boolean.TRUE.equals(overdueOnly)) {
                p.add(root.get("status").in(Status.OPEN, Status.IN_PROGRESS));
                p.add(cb.lessThan(root.get("dueAt"), Instant.now()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return PageResponse.from(tickets.findAll(spec, pageable).map(t -> toSummary(t, true)));
    }

    @Transactional(readOnly = true)
    public QueueStatsDto stats() {
        List<Ticket> all = tickets.findAll();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Status s : Status.values()) {
            byStatus.put(s.name(), all.stream().filter(t -> t.getStatus() == s).count());
        }
        Map<String, Long> openByCategory = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            openByCategory.put(c.name(), all.stream().filter(t -> t.getCategory() == c
                    && (t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS)).count());
        }
        long overdue = all.stream().filter(Ticket::isOverdue).count();
        long unassigned = all.stream().filter(t -> t.getAssignee() == null
                && (t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS)).count();
        OptionalDouble csat = all.stream().filter(t -> t.getSatisfaction() != null).mapToInt(Ticket::getSatisfaction).average();
        return new QueueStatsDto(byStatus, openByCategory, overdue, unassigned,
                csat.isPresent() ? Math.round(csat.getAsDouble() * 10) / 10.0 : null);
    }

    /** Assign, re-prioritise (moves the deadline) or change status; RESOLVED requires a resolution note. */
    @Transactional
    public TicketDto update(Long id, UpdateTicketRequest req) {
        Long me = me();
        Ticket t = find(id);
        if (t.getStatus() == Status.CLOSED) {
            throw new BusinessException("This ticket is closed");
        }
        if (Boolean.TRUE.equals(req.unassign())) {
            t.setAssignee(null);
        } else if (req.assigneeId() != null) {
            Employee a = employees.findById(req.assigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee " + req.assigneeId() + " not found"));
            boolean isAgent = users.findByEmployeeId(a.getId())
                    .map(u -> u.getRoles().stream().anyMatch(r -> r.getPermissions().stream()
                            .anyMatch(p -> AGENT_PERMISSION.equals(p.getCode()))))
                    .orElse(false);
            if (!isAgent) {
                throw new BadRequestException(a.getFullName() + " is not a helpdesk agent");
            }
            t.setAssignee(a);
        }
        if (req.priority() != null && req.priority() != t.getPriority()) {
            t.setPriority(req.priority());
            t.setDueAt(t.getCreatedAt().plus(req.priority().sla));
        }
        if (req.status() != null && req.status() != t.getStatus()) {
            switch (req.status()) {
                case RESOLVED -> {
                    if (req.resolutionNote() == null || req.resolutionNote().isBlank()) {
                        throw new BadRequestException("Add a resolution note when resolving a ticket");
                    }
                    t.setResolutionNote(req.resolutionNote().trim());
                    t.setResolvedAt(Instant.now());
                }
                case CLOSED -> t.setClosedAt(Instant.now());
                case IN_PROGRESS, OPEN -> t.setResolvedAt(null);
            }
            if (t.getFirstResponseAt() == null && req.status() != Status.OPEN) {
                t.setFirstResponseAt(Instant.now());
            }
            t.setStatus(req.status());
        }
        return toDto(t, me);
    }

    // ---------------------------------------------------------------- helpers

    private boolean isAgent() {
        return currentUser.hasAuthority(AGENT_PERMISSION);
    }

    private Long me() {
        return currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
    }

    private Ticket find(Long id) {
        return tickets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket " + id + " not found"));
    }

    private Ticket findVisible(Long id, Long me) {
        Ticket t = find(id);
        if (!isAgent() && !t.getRequester().getId().equals(me)) {
            throw new AccessDeniedException("Not allowed to view this ticket");
        }
        return t;
    }

    private TicketSummaryDto toSummary(Ticket t, boolean agentView) {
        long visibleComments = t.getComments().stream().filter(c -> agentView || !c.isInternalNote()).count();
        return new TicketSummaryDto(t.getId(), t.getTicketNo(), t.getSubject(), t.getCategory().name(),
                t.getPriority().name(), t.getStatus().name(), t.getRequester().getId(), t.getRequester().getFullName(),
                t.getAssignee() != null ? t.getAssignee().getId() : null,
                t.getAssignee() != null ? t.getAssignee().getFullName() : null,
                t.getDueAt(), t.isOverdue(), (int) visibleComments, t.getCreatedAt(), t.getUpdatedAt());
    }

    private TicketDto toDto(Ticket t, Long me) {
        boolean agent = isAgent();
        Long requesterId = t.getRequester().getId();
        List<CommentDto> comments = t.getComments().stream()
                .filter(c -> agent || !c.isInternalNote())
                .map(c -> new CommentDto(c.getId(), c.getAuthor().getId(), c.getAuthor().getFullName(), c.getBody(),
                        c.isInternalNote(), c.getAuthor().getId().equals(requesterId), c.getCreatedAt()))
                .toList();
        Employee r = t.getRequester();
        return new TicketDto(t.getId(), t.getTicketNo(), t.getSubject(), t.getDescription(), t.getCategory().name(),
                t.getPriority().name(), t.getStatus().name(), r.getId(), r.getFullName(),
                r.getDepartment() != null ? r.getDepartment().getName() : null,
                t.getAssignee() != null ? t.getAssignee().getId() : null,
                t.getAssignee() != null ? t.getAssignee().getFullName() : null,
                t.getDueAt(), t.isOverdue(), t.getFirstResponseAt(), t.getResolvedAt(), t.getClosedAt(),
                t.getResolutionNote(), t.getSatisfaction(), comments, agent, requesterId.equals(me), t.getCreatedAt());
    }
}
