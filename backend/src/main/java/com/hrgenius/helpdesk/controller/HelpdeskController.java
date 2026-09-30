package com.hrgenius.helpdesk.controller;

import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.helpdesk.dto.HelpdeskDtos.*;
import com.hrgenius.helpdesk.entity.Ticket.Category;
import com.hrgenius.helpdesk.entity.Ticket.Status;
import com.hrgenius.helpdesk.service.HelpdeskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Helpdesk API: anyone raises and follows their tickets; HELPDESK_AGENT works the queue. */
@Tag(name = "Helpdesk")
@RestController
@RequestMapping("/api/v1/helpdesk")
public class HelpdeskController {

    private static final String AGENT = "hasAuthority('HELPDESK_AGENT')";

    private final HelpdeskService helpdesk;
    private final CurrentUserService currentUser;

    public HelpdeskController(HelpdeskService helpdesk, CurrentUserService currentUser) {
        this.helpdesk = helpdesk;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Raise a ticket")
    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDto create(@Valid @RequestBody CreateTicketRequest req) {
        return helpdesk.create(me(), req);
    }

    @Operation(summary = "My tickets")
    @GetMapping("/tickets/mine")
    public List<TicketSummaryDto> mine() {
        return helpdesk.mine(me());
    }

    @Operation(summary = "The agent queue")
    @GetMapping("/tickets")
    @PreAuthorize(AGENT)
    public PageResponse<TicketSummaryDto> queue(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) Boolean overdue,
            @PageableDefault(size = 20, sort = "dueAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return helpdesk.queue(search, status, category, mine, overdue, pageable);
    }

    @Operation(summary = "Queue statistics")
    @GetMapping("/stats")
    @PreAuthorize(AGENT)
    public QueueStatsDto stats() {
        return helpdesk.stats();
    }

    @Operation(summary = "One ticket (requester or agent)")
    @GetMapping("/tickets/{id}")
    public TicketDto get(@PathVariable Long id) {
        return helpdesk.get(id);
    }

    @Operation(summary = "Reply (agents may add internal notes)")
    @PostMapping("/tickets/{id}/comments")
    public TicketDto comment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return helpdesk.comment(id, req);
    }

    @Operation(summary = "Triage: assign, prioritise, change status")
    @PatchMapping("/tickets/{id}")
    @PreAuthorize(AGENT)
    public TicketDto update(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest req) {
        return helpdesk.update(id, req);
    }

    @Operation(summary = "Close a ticket (requester may rate the support)")
    @PostMapping("/tickets/{id}/close")
    public TicketDto close(@PathVariable Long id, @Valid @RequestBody(required = false) CloseRequest req) {
        return helpdesk.close(id, req);
    }

    @Operation(summary = "Reopen a resolved ticket")
    @PostMapping("/tickets/{id}/reopen")
    public TicketDto reopen(@PathVariable Long id) {
        return helpdesk.reopen(id);
    }

    private Long me() {
        return currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
    }
}
