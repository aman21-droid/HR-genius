package com.hrgenius.employee.controller;

import com.hrgenius.common.web.Downloads;
import com.hrgenius.employee.dto.ProfileDtos.DocumentDto;
import com.hrgenius.employee.dto.ProfileDtos.ExpiringDocumentDto;
import com.hrgenius.employee.service.DocumentService;
import com.hrgenius.employee.service.DocumentService.DownloadedDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Documents")
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "Download a document (always as an attachment)")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        DownloadedDocument doc = documentService.download(id);
        return Downloads.attachment(doc.resource(), doc.fileName(), MediaType.parseMediaType(doc.contentType()));
    }

    @PatchMapping("/{id}/verify")
    public DocumentDto verify(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean verified) {
        return documentService.setVerified(id, verified);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        documentService.delete(id);
    }

    @Operation(summary = "Documents expired or expiring within N days (HR)")
    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<ExpiringDocumentDto> expiring(@RequestParam(defaultValue = "30") int days) {
        return documentService.expiring(Math.min(Math.max(days, 0), 365));
    }
}
