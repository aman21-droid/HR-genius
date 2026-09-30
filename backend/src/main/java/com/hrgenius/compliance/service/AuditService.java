package com.hrgenius.compliance.service;

import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.common.util.SearchPredicates;
import com.hrgenius.compliance.dto.AuditLogDto;
import com.hrgenius.compliance.entity.AuditLog;
import com.hrgenius.compliance.entity.AuditLog.AuditAction;
import com.hrgenius.compliance.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Writes and queries the audit trail. Writes join the caller's transaction, so an entry
 * exists only if the audited change actually commits.
 */
@Service
public class AuditService {

    private static final int MAX_VALUE_LENGTH = 2000;

    private final AuditLogRepository repository;
    private final CurrentUserService currentUser;

    public AuditService(AuditLogRepository repository, CurrentUserService currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    /** Record a single-field change. No-op when old and new are equal. */
    @Transactional
    public void recordChange(String entity, Object entityId, String field, Object oldValue, Object newValue) {
        String oldStr = stringify(oldValue);
        String newStr = stringify(newValue);
        if (Objects.equals(oldStr, newStr)) {
            return;
        }
        save(entity, entityId, AuditAction.UPDATE, field, oldStr, newStr);
    }

    @Transactional
    public void record(String entity, Object entityId, AuditAction action, String detail) {
        save(entity, entityId, action, null, null, detail);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> search(String entity, String entityId, String actor,
                                            AuditAction action, Instant from, Instant to,
                                            Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (entity != null && !entity.isBlank()) {
                p.add(cb.equal(root.get("entity"), entity));
            }
            if (entityId != null && !entityId.isBlank()) {
                p.add(cb.equal(root.get("entityId"), entityId));
            }
            if (actor != null && !actor.isBlank()) {
                p.add(SearchPredicates.containsIgnoreCase(cb, root.get("actor"), actor));
            }
            if (action != null) {
                p.add(cb.equal(root.get("action"), action));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("changedAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("changedAt"), to));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(AuditService::toDto));
    }

    private void save(String entity, Object entityId, AuditAction action, String field,
                      String oldValue, String newValue) {
        AuditLog log = new AuditLog();
        log.setEntity(entity);
        log.setEntityId(entityId == null ? null : entityId.toString());
        log.setAction(action);
        log.setField(field);
        log.setOldValue(truncate(oldValue));
        log.setNewValue(truncate(newValue));
        log.setActor(currentUser.email());
        log.setChangedAt(Instant.now());
        repository.save(log);
    }

    private static String stringify(Object value) {
        return value == null ? null : value.toString();
    }

    private static String truncate(String value) {
        return value != null && value.length() > MAX_VALUE_LENGTH ? value.substring(0, MAX_VALUE_LENGTH) : value;
    }

    private static AuditLogDto toDto(AuditLog a) {
        return new AuditLogDto(a.getId(), a.getEntity(), a.getEntityId(), a.getAction().name(),
                a.getField(), a.getOldValue(), a.getNewValue(), a.getActor(), a.getChangedAt());
    }
}
