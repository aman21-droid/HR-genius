package com.hrgenius.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Minimal plain-text mail sender (MailHog in dev). Failures are logged, never thrown, so a
 * mail outage cannot break the business operation or scheduled job that triggered the email.
 * Phase 3 builds the full notification centre on top of this.
 */
@Slf4j
@Service
public class MailService {

    private final JavaMailSender sender;
    private final String from;

    public MailService(JavaMailSender sender, @Value("${hrgenius.alerts.from-email}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public boolean send(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            return true;
        } catch (MailException e) {
            log.warn("Could not send email '{}' to {}: {}", subject, to, e.getMessage());
            return false;
        }
    }
}
