package com.hrgenius.employee.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.EmployeeEnums.DocumentCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

/**
 * Metadata for a file in the document vault. The bytes live in file storage under
 * {@code storageKey} (a random UUID); the user-supplied filename is display-only.
 */
@Getter
@Setter
@Entity
@Table(name = "employee_documents")
@SQLRestriction("deleted = 0")
public class EmployeeDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "employee_document_seq_gen")
    @SequenceGenerator(name = "employee_document_seq_gen", sequenceName = "employee_document_seq", allocationSize = 1)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private DocumentCategory category;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 80)
    private String storageKey;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "notes", length = 500)
    private String notes;
}
