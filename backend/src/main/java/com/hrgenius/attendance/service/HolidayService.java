package com.hrgenius.attendance.service;

import com.hrgenius.attendance.dto.AttendanceDtos.HolidayDto;
import com.hrgenius.attendance.dto.AttendanceDtos.HolidayRequest;
import com.hrgenius.attendance.entity.Holiday;
import com.hrgenius.attendance.repository.HolidayRepository;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.org.entity.Location;
import com.hrgenius.org.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Holiday calendar management. Reads are open; writes are guarded by LEAVE_CONFIG. */
@Service
public class HolidayService {

    private final HolidayRepository holidays;
    private final LocationRepository locations;

    public HolidayService(HolidayRepository holidays, LocationRepository locations) {
        this.holidays = holidays;
        this.locations = locations;
    }

    @Transactional(readOnly = true)
    public List<HolidayDto> listByYear(int year) {
        return holidays.findByYearOrderByHolidayDateAsc(year).stream().map(HolidayService::toDto).toList();
    }

    @Transactional
    public HolidayDto create(HolidayRequest req) {
        Holiday h = new Holiday();
        apply(h, req);
        return toDto(holidays.save(h));
    }

    @Transactional
    public HolidayDto update(Long id, HolidayRequest req) {
        Holiday h = holidays.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday " + id + " not found"));
        apply(h, req);
        return toDto(h);
    }

    @Transactional
    public void delete(Long id) {
        Holiday h = holidays.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday " + id + " not found"));
        h.setDeleted(true);
    }

    private void apply(Holiday h, HolidayRequest req) {
        h.setHolidayDate(req.holidayDate());
        h.setName(req.name().trim());
        h.setOptionalHoliday(Boolean.TRUE.equals(req.optionalHoliday()));
        h.setYear(req.holidayDate().getYear());
        if (req.locationId() != null) {
            Location loc = locations.findById(req.locationId())
                    .orElseThrow(() -> new BusinessException("Location " + req.locationId() + " not found"));
            h.setLocation(loc);
        } else {
            h.setLocation(null);
        }
    }

    static HolidayDto toDto(Holiday h) {
        Location loc = h.getLocation();
        return new HolidayDto(h.getId(), h.getHolidayDate(), h.getName(), h.isOptionalHoliday(),
                loc == null ? null : loc.getId(), loc == null ? null : loc.getName(), h.getYear());
    }
}
