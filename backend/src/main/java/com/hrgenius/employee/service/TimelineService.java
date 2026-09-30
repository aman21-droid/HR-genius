package com.hrgenius.employee.service;

import com.hrgenius.employee.dto.ProfileDtos.TimelineEventDto;
import com.hrgenius.employee.entity.EmployeeEnums.TimelineEventType;
import com.hrgenius.employee.entity.TimelineEvent;
import com.hrgenius.employee.repository.TimelineEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TimelineService {

    private final TimelineEventRepository repository;

    public TimelineService(TimelineEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void add(Long employeeId, TimelineEventType type, LocalDate date, String title, String description) {
        TimelineEvent e = new TimelineEvent();
        e.setEmployeeId(employeeId);
        e.setEventType(type);
        e.setEventDate(date);
        e.setTitle(title);
        e.setDescription(description);
        repository.save(e);
    }

    @Transactional(readOnly = true)
    public List<TimelineEventDto> list(Long employeeId) {
        return repository.findByEmployeeIdOrderByEventDateDescIdDesc(employeeId).stream()
                .map(e -> new TimelineEventDto(e.getId(), e.getEventType().name(), e.getEventDate(),
                        e.getTitle(), e.getDescription(), e.getCreatedBy()))
                .toList();
    }
}
