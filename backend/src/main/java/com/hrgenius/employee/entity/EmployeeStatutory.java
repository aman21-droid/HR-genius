package com.hrgenius.employee.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.common.security.CryptoConverter;
import com.hrgenius.employee.entity.EmployeeEnums.TaxRegime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bank and statutory identifiers, one row per employee (PK = employee id).
 * PAN, Aadhaar and bank account number are AES-GCM encrypted at rest via {@link CryptoConverter}.
 */
@Getter
@Setter
@Entity
@Table(name = "employee_statutory")
public class EmployeeStatutory extends BaseEntity {

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "pan_enc", length = 256)
    private String pan;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "aadhaar_enc", length = 256)
    private String aadhaar;

    /** Universal Account Number (PF). Not secret on its own; stored in clear. */
    @Column(name = "uan", length = 20)
    private String uan;

    @Column(name = "esi_number", length = 20)
    private String esiNumber;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "account_holder_name", length = 160)
    private String accountHolderName;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "bank_account_enc", length = 256)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc", length = 15)
    private String bankIfsc;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", length = 10)
    private TaxRegime taxRegime;
}
