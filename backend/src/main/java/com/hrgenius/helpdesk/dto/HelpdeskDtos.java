package com.hrgenius.helpdesk.dto;

import com.hrgenius.helpdesk.entity.Ticket.Category;
import com.hrgenius.helpdesk.entity.Ticket.Priority;
import com.hrgenius.helpdesk.entity.Ticket.Status;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response shapes for the helpdesk API. */
public final class HelpdeskDtos {

    private HelpdeskDtos() {
    }

    public record TicketSummaryDto(Long id, String ticketNo, String subject, String category, String priority,
                                   String status, Long requesterId, String requesterName, Long assigneeId,
                                   String assigneeName, Instant dueAt, boolean overdue, int comments,
                                   Instant createdAt, Instant updatedAt) {
    }

    public record CommentDto(Long id, Long authorId, String authorName, String body, boolean internalNote,
                             boolean byRequester, Instant createdAt) {
    }

    public record TicketDto(Long id, String ticketNo, String subject, String description, String category,
                            String priority, String status, Long requesterId, String requesterName,
                            String requesterDepartment, Long assigneeId, String assigneeName, Instant dueAt,
                            boolean overdue, Instant firstResponseAt, Instant resolvedAt, Instant closedAt,
                            String resolutionNote, Integer satisfaction, List<CommentDto> comments,
                            boolean viewerIsAgent, boolean viewerIsRequester, Instant createdAt) {
    }

    public record CreateTicketRequest(
            @NotNull Category category,
            Priority priority,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 4000) String description) {
    }

    public record CommentRequest(
            @NotBlank @Size(max = 4000) String body,
            Boolean internal) {
    }

    /** Agent triage: any subset of assignee / priority / status (+ resolution note when resolving). */
    public record UpdateTicketRequest(
            Long assigneeId,
            Boolean unassign,
            Priority priority,
            Status status,
            @Size(max = 2000) String resolutionNote) {
    }

    public record CloseRequest(@Min(1) @Max(5) Integer satisfaction) {
    }

    public record QueueStatsDto(Map<String, Long> byStatus, Map<String, Long> openByCategory, long overdue,
                                long unassigned, Double averageSatisfaction) {
    }
}
