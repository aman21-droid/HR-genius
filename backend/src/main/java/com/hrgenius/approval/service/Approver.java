package com.hrgenius.approval.service;

/** One approver in a chain: the employee who must decide, and a label for why they were chosen. */
public record Approver(Long employeeId, String roleHint) {
}
