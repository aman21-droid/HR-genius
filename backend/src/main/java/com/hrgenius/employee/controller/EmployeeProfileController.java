package com.hrgenius.employee.controller;

import com.hrgenius.employee.dto.ProfileDtos.*;
import com.hrgenius.employee.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Profile tabs: timeline, bank & statutory, emergency contacts, documents, assets. */
@Tag(name = "Employee profile")
@RestController
@RequestMapping("/api/v1/employees/{employeeId}")
public class EmployeeProfileController {

    private final TimelineService timelineService;
    private final StatutoryService statutoryService;
    private final EmergencyContactService contactService;
    private final DocumentService documentService;
    private final AssetService assetService;
    private final EmployeeService employeeService;
    private final EmployeeAccessService access;

    public EmployeeProfileController(TimelineService timelineService, StatutoryService statutoryService,
                                     EmergencyContactService contactService, DocumentService documentService,
                                     AssetService assetService, EmployeeService employeeService,
                                     EmployeeAccessService access) {
        this.timelineService = timelineService;
        this.statutoryService = statutoryService;
        this.contactService = contactService;
        this.documentService = documentService;
        this.assetService = assetService;
        this.employeeService = employeeService;
        this.access = access;
    }

    // ---- timeline ----
    @GetMapping("/timeline")
    public List<TimelineEventDto> timeline(@PathVariable Long employeeId) {
        employeeService.get(employeeId);            // existence + leaver visibility
        access.requireFullProfile(employeeId);
        return timelineService.list(employeeId);
    }

    // ---- bank & statutory ----
    @Operation(summary = "Bank & statutory IDs; masked unless reveal=true (reveals are audited)")
    @GetMapping("/statutory")
    public StatutoryDto statutory(@PathVariable Long employeeId,
                                  @RequestParam(defaultValue = "false") boolean reveal) {
        return statutoryService.get(employeeId, reveal);
    }

    @Operation(summary = "Update bank & statutory IDs (PATCH: omitted/null fields are left unchanged)")
    @PatchMapping("/statutory")
    public StatutoryDto updateStatutory(@PathVariable Long employeeId, @Valid @RequestBody StatutoryRequest request) {
        return statutoryService.update(employeeId, request);
    }

    // ---- emergency contacts ----
    @GetMapping("/emergency-contacts")
    public List<EmergencyContactDto> contacts(@PathVariable Long employeeId) {
        return contactService.list(employeeId);
    }

    @PostMapping("/emergency-contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyContactDto addContact(@PathVariable Long employeeId,
                                          @Valid @RequestBody EmergencyContactRequest request) {
        return contactService.create(employeeId, request);
    }

    @PutMapping("/emergency-contacts/{contactId}")
    public EmergencyContactDto updateContact(@PathVariable Long employeeId, @PathVariable Long contactId,
                                             @Valid @RequestBody EmergencyContactRequest request) {
        return contactService.update(employeeId, contactId, request);
    }

    @DeleteMapping("/emergency-contacts/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContact(@PathVariable Long employeeId, @PathVariable Long contactId) {
        contactService.delete(employeeId, contactId);
    }

    // ---- documents ----
    @GetMapping("/documents")
    public List<DocumentDto> documents(@PathVariable Long employeeId) {
        return documentService.list(employeeId);
    }

    @Operation(summary = "Upload a document (PDF, PNG, JPEG or DOCX; max 10 MB)")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto upload(@PathVariable Long employeeId,
                              @Valid @ModelAttribute DocumentUploadRequest meta,
                              @RequestParam("file") MultipartFile file) {
        return documentService.upload(employeeId, meta, file);
    }

    // ---- assets ----
    @GetMapping("/assets")
    public List<AssetAssignmentDto> assets(@PathVariable Long employeeId) {
        return assetService.forEmployee(employeeId);
    }
}
