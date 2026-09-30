package com.hrgenius.employee.dto;

import com.hrgenius.employee.entity.Asset.AssetCategory;
import com.hrgenius.employee.entity.Asset.AssetStatus;
import com.hrgenius.employee.entity.EmployeeEnums.DocumentCategory;
import com.hrgenius.employee.entity.EmployeeEnums.TaxRegime;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Payloads for the profile tabs: timeline, bank & statutory, contacts, documents, assets. */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    // ---- timeline ----
    public record TimelineEventDto(Long id, String eventType, LocalDate eventDate, String title,
                                   String description, String recordedBy) {
    }

    // ---- bank & statutory ----
    /** @param masked true when PAN/Aadhaar/account are shown as XXXX1234 */
    public record StatutoryDto(Long employeeId, String pan, String aadhaar, String uan, String esiNumber,
                               String bankName, String accountHolderName, String bankAccountNumber,
                               String bankIfsc, String taxRegime, boolean masked) {
    }

    public record StatutoryRequest(
            @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "must be a valid PAN like ABCDE1234F") String pan,
            @Pattern(regexp = "^[2-9][0-9]{11}$", message = "must be a 12-digit Aadhaar number") String aadhaar,
            @Pattern(regexp = "^[0-9]{12}$", message = "must be a 12-digit UAN") String uan,
            @Pattern(regexp = "^[0-9]{10,17}$", message = "must be 10-17 digits") String esiNumber,
            @Size(max = 120) String bankName,
            @Size(max = 160) String accountHolderName,
            @Pattern(regexp = "^[0-9]{9,18}$", message = "must be 9-18 digits") String bankAccountNumber,
            @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "must be a valid IFSC like HDFC0001234") String bankIfsc,
            TaxRegime taxRegime) {
    }

    // ---- emergency contacts ----
    public record EmergencyContactDto(Long id, String name, String relationship, String phone,
                                      String email, boolean primary) {
    }

    public record EmergencyContactRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 40) String relationship,
            @NotBlank @Pattern(regexp = "^[+0-9 ()-]{7,30}$", message = "must be a valid phone number") String phone,
            @Email @Size(max = 160) String email,
            boolean primary) {
    }

    // ---- documents ----
    public record DocumentDto(Long id, Long employeeId, String category, String title, String fileName,
                              String contentType, long sizeBytes, LocalDate expiryDate, Long daysToExpiry,
                              boolean verified, String notes, String uploadedBy, java.time.Instant uploadedAt) {
    }

    /** Metadata part of a multipart upload (the file itself is a separate part). */
    public record DocumentUploadRequest(
            @NotNull DocumentCategory category,
            @NotBlank @Size(max = 160) String title,
            LocalDate expiryDate,
            @Size(max = 500) String notes) {
    }

    /** Row in the HR "expiring documents" list; negative daysToExpiry means already expired. */
    public record ExpiringDocumentDto(Long documentId, Long employeeId, String employeeCode, String employeeName,
                                      String category, String title, LocalDate expiryDate, long daysToExpiry) {
    }

    // ---- assets ----
    public record AssetDto(Long id, String assetTag, String name, String category, String serialNumber,
                           String status, LocalDate purchaseDate, BigDecimal purchaseCost, String notes,
                           Long currentEmployeeId, String currentEmployeeName, String currentEmployeeCode) {
    }

    public record AssetRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{2,40}$", message = "use 2-40 letters, digits or '-'") String assetTag,
            @NotBlank @Size(max = 160) String name,
            @NotNull AssetCategory category,
            @Size(max = 80) String serialNumber,
            AssetStatus status,
            @PastOrPresent LocalDate purchaseDate,
            @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal purchaseCost,
            @Size(max = 500) String notes) {
    }

    public record AssignAssetRequest(@NotNull Long employeeId, LocalDate assignedOn, @Size(max = 500) String notes) {
    }

    public record ReturnAssetRequest(LocalDate returnedOn,
                                     @NotBlank @Size(max = 40) String condition,
                                     @Size(max = 500) String notes) {
    }

    public record AssetAssignmentDto(Long id, Long assetId, String assetTag, String assetName, String category,
                                     String serialNumber, Long employeeId, LocalDate assignedOn,
                                     LocalDate returnedOn, String assignNotes, String returnCondition,
                                     String returnNotes) {
    }
}
