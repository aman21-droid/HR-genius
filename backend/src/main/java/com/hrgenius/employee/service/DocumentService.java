package com.hrgenius.employee.service;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.storage.FileStorageService;
import com.hrgenius.compliance.entity.AuditLog.AuditAction;
import com.hrgenius.compliance.service.AuditService;
import com.hrgenius.employee.dto.ProfileDtos.DocumentDto;
import com.hrgenius.employee.dto.ProfileDtos.DocumentUploadRequest;
import com.hrgenius.employee.dto.ProfileDtos.ExpiringDocumentDto;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeDocument;
import com.hrgenius.employee.repository.EmployeeDocumentRepository;
import com.hrgenius.employee.repository.EmployeeRepository;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Document vault. Employees can upload their own documents (e.g. during onboarding); HR can
 * upload for anyone and mark documents verified. Deletion is HR-only.
 */
@Service
public class DocumentService {

    private final EmployeeDocumentRepository repository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final EmployeeAccessService access;
    private final FileStorageService storage;
    private final AuditService audit;

    public DocumentService(EmployeeDocumentRepository repository, EmployeeRepository employeeRepository,
                           EmployeeService employeeService, EmployeeAccessService access,
                           FileStorageService storage, AuditService audit) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.access = access;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> list(Long employeeId) {
        employeeService.find(employeeId);
        access.requireFullProfile(employeeId);
        return repository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream().map(DocumentService::toDto).toList();
    }

    @Transactional
    public DocumentDto upload(Long employeeId, DocumentUploadRequest meta, MultipartFile file) {
        employeeService.find(employeeId);
        access.requireSelfOrEditor(employeeId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a file to upload");
        }
        if (meta.expiryDate() != null && meta.expiryDate().isBefore(LocalDate.now().minusYears(10))) {
            throw new BadRequestException("Expiry date looks wrong");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
        String key = storage.store(bytes, file.getContentType());

        EmployeeDocument d = new EmployeeDocument();
        d.setEmployeeId(employeeId);
        d.setCategory(meta.category());
        d.setTitle(meta.title().trim());
        d.setOriginalFileName(sanitizeFileName(file.getOriginalFilename()));
        d.setContentType(file.getContentType());
        d.setSizeBytes((long) bytes.length);
        d.setStorageKey(key);
        d.setExpiryDate(meta.expiryDate());
        d.setNotes(meta.notes());
        // Self-uploads await HR verification; HR uploads are trusted.
        d.setVerified(access.canEdit());
        repository.save(d);
        return toDto(d);
    }

    /** Returns the stored file plus its metadata, after an access check. */
    @Transactional(readOnly = true)
    public DownloadedDocument download(Long documentId) {
        EmployeeDocument d = find(documentId);
        access.requireFullProfile(d.getEmployeeId());
        return new DownloadedDocument(storage.load(d.getStorageKey()), d.getOriginalFileName(), d.getContentType());
    }

    @Transactional
    public DocumentDto setVerified(Long documentId, boolean verified) {
        requireEditor();
        EmployeeDocument d = find(documentId);
        d.setVerified(verified);
        return toDto(d);
    }

    @Transactional
    public void delete(Long documentId) {
        requireEditor();
        EmployeeDocument d = find(documentId);
        d.setDeleted(true);
        storage.delete(d.getStorageKey());
        audit.record("EmployeeDocument", documentId, AuditAction.DELETE,
                d.getTitle() + " (employee " + d.getEmployeeId() + ")");
    }

    /** Expired or expiring within {@code days}, for current employees only. */
    @Transactional(readOnly = true)
    public List<ExpiringDocumentDto> expiring(int days) {
        LocalDate today = LocalDate.now();
        List<EmployeeDocument> docs = repository.findExpiringOnOrBefore(today.plusDays(days));
        Set<Long> ids = docs.stream().map(EmployeeDocument::getEmployeeId).collect(Collectors.toSet());
        Map<Long, Employee> employees = employeeRepository.findAllById(ids).stream()
                .filter(e -> !e.isDeleted() && e.getStatus() != com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus.EXITED)
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        return docs.stream()
                .filter(d -> employees.containsKey(d.getEmployeeId()))
                .map(d -> {
                    Employee e = employees.get(d.getEmployeeId());
                    return new ExpiringDocumentDto(d.getId(), e.getId(), e.getEmployeeCode(), e.getFullName(),
                            d.getCategory().name(), d.getTitle(), d.getExpiryDate(),
                            ChronoUnit.DAYS.between(today, d.getExpiryDate()));
                })
                .toList();
    }

    private void requireEditor() {
        if (!access.canEdit()) {
            throw new AccessDeniedException("Only HR can do this");
        }
    }

    private EmployeeDocument find(Long id) {
        return repository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
    }

    /** Keeps the name for display only; strips path parts and control characters. */
    static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "document";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (base.length() > 200) {
            base = base.substring(base.length() - 200);
        }
        return base.isEmpty() ? "document" : base;
    }

    private static DocumentDto toDto(EmployeeDocument d) {
        Long days = d.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), d.getExpiryDate());
        return new DocumentDto(d.getId(), d.getEmployeeId(), d.getCategory().name(), d.getTitle(),
                d.getOriginalFileName(), d.getContentType(), d.getSizeBytes(), d.getExpiryDate(), days,
                d.isVerified(), d.getNotes(), d.getCreatedBy(), d.getCreatedAt());
    }

    public record DownloadedDocument(Resource resource, String fileName, String contentType) {
    }
}
