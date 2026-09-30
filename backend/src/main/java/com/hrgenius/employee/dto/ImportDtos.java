package com.hrgenius.employee.dto;

import java.util.List;

public final class ImportDtos {

    private ImportDtos() {
    }

    /**
     * Result of a bulk import. Import is all-or-nothing: {@code created} is 0 whenever
     * {@code errors} is non-empty or {@code dryRun} is true.
     */
    public record ImportReport(int totalRows, int validRows, int created, boolean dryRun, List<ImportError> errors) {
    }

    /** @param row spreadsheet row number as the user sees it (header is row 1) */
    public record ImportError(int row, String column, String message) {
    }
}
