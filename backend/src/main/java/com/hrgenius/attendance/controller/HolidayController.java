package com.hrgenius.attendance.controller;

import com.hrgenius.attendance.dto.AttendanceDtos.HolidayDto;
import com.hrgenius.attendance.dto.AttendanceDtos.HolidayRequest;
import com.hrgenius.attendance.service.HolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Holiday calendar. Reads are open to any authenticated user; writes require LEAVE_CONFIG. */
@Tag(name = "Holidays")
@RestController
@RequestMapping("/api/v1/holidays")
public class HolidayController {

    private final HolidayService holidays;

    public HolidayController(HolidayService holidays) {
        this.holidays = holidays;
    }

    @Operation(summary = "Holidays for a year (defaults to current year)")
    @GetMapping
    public List<HolidayDto> list(@RequestParam(required = false) Integer year) {
        return holidays.listByYear(year == null ? LocalDate.now().getYear() : year);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LEAVE_CONFIG')")
    public HolidayDto create(@Valid @RequestBody HolidayRequest req) {
        return holidays.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAVE_CONFIG')")
    public HolidayDto update(@PathVariable Long id, @Valid @RequestBody HolidayRequest req) {
        return holidays.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('LEAVE_CONFIG')")
    public void delete(@PathVariable Long id) {
        holidays.delete(id);
    }
}
