package com.hrgenius.performance.service;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.performance.dto.PerformanceDtos.FeedbackDto;
import com.hrgenius.performance.dto.PerformanceDtos.FeedbackRequest;
import com.hrgenius.performance.entity.FeedbackNote;
import com.hrgenius.performance.entity.PerformanceEnums.FeedbackKind;
import com.hrgenius.performance.entity.PerformanceEnums.Visibility;
import com.hrgenius.performance.repository.FeedbackNoteRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Continuous feedback. PUBLIC notes appear on the company kudos wall (praise only); PRIVATE notes
 * are visible to the author, the recipient and the recipient's manager.
 */
@Service
public class FeedbackService {

    private final FeedbackNoteRepository notes;
    private final EmployeeRepository employees;

    public FeedbackService(FeedbackNoteRepository notes, EmployeeRepository employees) {
        this.notes = notes;
        this.employees = employees;
    }

    @Transactional
    public FeedbackDto give(Long fromEmpId, FeedbackRequest req) {
        if (fromEmpId.equals(req.toEmployeeId())) {
            throw new BadRequestException("You cannot give feedback to yourself");
        }
        if (req.kind() == FeedbackKind.CONSTRUCTIVE && req.visibility() == Visibility.PUBLIC) {
            throw new BadRequestException("Constructive feedback is always private");
        }
        Employee to = employees.findById(req.toEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + req.toEmployeeId() + " not found"));
        if (to.getStatus() == EmployeeStatus.EXITED) {
            throw new BadRequestException(to.getFullName() + " has left the company");
        }
        FeedbackNote n = new FeedbackNote();
        n.setAuthor(employees.getReferenceById(fromEmpId));
        n.setRecipient(to);
        n.setKind(req.kind());
        n.setVisibility(req.visibility());
        n.setMessage(req.message().trim());
        notes.save(n);
        return toDto(notes.findById(n.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<FeedbackDto> wall(int limit) {
        return notes.findWall(Visibility.PUBLIC, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
                .stream().map(FeedbackService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackDto> received(Long empId) {
        return notes.findReceived(empId).stream().map(FeedbackService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackDto> given(Long empId) {
        return notes.findGiven(empId).stream().map(FeedbackService::toDto).toList();
    }

    /** Private notes about the manager's direct reports. */
    @Transactional(readOnly = true)
    public List<FeedbackDto> aboutMyTeam(Long managerEmpId) {
        return notes.findPrivateForTeamOf(managerEmpId).stream().map(FeedbackService::toDto).toList();
    }

    private static FeedbackDto toDto(FeedbackNote n) {
        return new FeedbackDto(n.getId(), n.getAuthor().getId(), n.getAuthor().getFullName(), n.getRecipient().getId(),
                n.getRecipient().getFullName(), n.getKind().name(), n.getVisibility().name(), n.getMessage(), n.getCreatedAt());
    }
}
