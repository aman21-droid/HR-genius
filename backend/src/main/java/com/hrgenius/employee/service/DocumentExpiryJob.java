package com.hrgenius.employee.service;

import com.hrgenius.employee.dto.ProfileDtos.ExpiringDocumentDto;
import com.hrgenius.notification.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Daily digest to HR of documents that have expired or will expire soon. */
@Slf4j
@Component
public class DocumentExpiryJob {

    private final DocumentService documentService;
    private final MailService mailService;
    private final int windowDays;
    private final String hrEmail;

    public DocumentExpiryJob(DocumentService documentService, MailService mailService,
                             @Value("${hrgenius.alerts.document-expiry-days}") int windowDays,
                             @Value("${hrgenius.alerts.hr-email}") String hrEmail) {
        this.documentService = documentService;
        this.mailService = mailService;
        this.windowDays = windowDays;
        this.hrEmail = hrEmail;
    }

    @Scheduled(cron = "${hrgenius.alerts.document-expiry-cron}")
    public void run() {
        List<ExpiringDocumentDto> docs = documentService.expiring(windowDays);
        if (docs.isEmpty()) {
            log.info("Document expiry check: nothing expiring in the next {} days", windowDays);
            return;
        }
        mailService.send(hrEmail, "Document expiry alert: " + docs.size() + " document(s) need attention",
                buildBody(docs, windowDays));
        log.info("Document expiry check: {} document(s) flagged", docs.size());
    }

    static String buildBody(List<ExpiringDocumentDto> docs, int windowDays) {
        StringBuilder sb = new StringBuilder("The following documents have expired or expire within ")
                .append(windowDays).append(" days:\n\n");
        for (ExpiringDocumentDto d : docs) {
            String when = d.daysToExpiry() < 0 ? "EXPIRED " + (-d.daysToExpiry()) + " day(s) ago"
                    : d.daysToExpiry() == 0 ? "expires TODAY"
                    : "expires in " + d.daysToExpiry() + " day(s)";
            sb.append("- ").append(d.employeeName()).append(" (").append(d.employeeCode()).append("): ")
                    .append(d.title()).append(" [").append(d.category()).append("] ")
                    .append(when).append(" on ").append(d.expiryDate()).append('\n');
        }
        sb.append("\nReview them in HRGenius > Employees > Documents.");
        return sb.toString();
    }
}
