package com.hrgenius.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/** API payloads for holidays, attendance days, and regularization. */
public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    public record HolidayDto(
            Long id, LocalDate holidayDate, String name, boolean optionalHoliday,
            Long locationId, String locationName, int year) {
    }

    public record HolidayRequest(
            @NotNull LocalDate holidayDate,
            @NotBlank @Size(max = 120) String name,
            Boolean optionalHoliday,
            Long locationId) {
    }

    public record AttendanceDayDto(
            Long id, Long employeeId, String employeeName, LocalDate workDate,
            Instant checkIn, Instant checkOut, int workedMinutes, String status, String source, String remarks) {
    }

    /** One day on the attendance calendar, synthesized where no punch record exists. */
    public record CalendarDay(
            LocalDate date, String status, Instant checkIn, Instant checkOut,
            Integer workedMinutes, String label) {
    }

    /** Optional note attached to a self check-in / check-out. */
    public record PunchRequest(@Size(max = 300) String remarks) {
    }

    /** HR edit of a single day. */
    public record AttendanceEditRequest(
            Instant checkIn,
            Instant checkOut,
            String status,
            @Size(max = 300) String remarks) {
    }

    public record RegularizationDto(
            Long id, Long employeeId, String employeeName, LocalDate workDate,
            Instant requestedCheckIn, Instant requestedCheckOut, String reason, String status,
            Long approvalRequestId, Instant decidedAt, Instant createdAt) {
    }

    public record RegularizationRequestBody(
            @NotNull LocalDate workDate,
            Instant requestedCheckIn,
            Instant requestedCheckOut,
            @NotBlank @Size(max = 500) String reason) {
    }
}
