package com.hrgenius.employee.service;

import com.hrgenius.common.util.MaskingUtil;
import com.hrgenius.employee.repository.EmployeeRepository.ManagerLink;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure-logic tests: no Spring context, no database. */
class EmployeeLogicTest {

    record Link(Long id, Long managerId) implements ManagerLink {
        public Long getId() { return id; }
        public Long getManagerId() { return managerId; }
    }

    //        1
    //      /   \
    //     2     3
    //    / \     \
    //   4   5     6
    //   |
    //   7
    private static final List<Link> TREE = List.of(
            new Link(1L, null), new Link(2L, 1L), new Link(3L, 1L), new Link(4L, 2L),
            new Link(5L, 2L), new Link(6L, 3L), new Link(7L, 4L));

    @Test
    void reportingTreeIncludesAllLevelsButNotTheRoot() {
        assertThat(EmployeeAccessService.collectDescendants(2L, TREE)).containsExactlyInAnyOrder(4L, 5L, 7L);
        assertThat(EmployeeAccessService.collectDescendants(1L, TREE)).hasSize(6).doesNotContain(1L);
    }

    @Test
    void leafHasEmptyTree() {
        assertThat(EmployeeAccessService.collectDescendants(7L, TREE)).isEmpty();
    }

    @Test
    void cycleInDataDoesNotLoopForever() {
        List<Link> cyclic = List.of(new Link(1L, 3L), new Link(2L, 1L), new Link(3L, 2L));
        Set<Long> result = EmployeeAccessService.collectDescendants(1L, cyclic);
        assertThat(result).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void maskingKeepsOnlyLastFour() {
        assertThat(MaskingUtil.mask("ABCDE1234F")).isEqualTo("XXXXXX234F");
        assertThat(MaskingUtil.mask("123")).isEqualTo("XXX");
        assertThat(MaskingUtil.mask(null)).isNull();
    }

    @Test
    void temporaryPasswordMeetsPolicy() {
        for (int i = 0; i < 50; i++) {
            String p = EmployeeService.generateTemporaryPassword();
            assertThat(p).hasSize(14).matches(".*[A-Z].*").matches(".*[a-z].*")
                    .matches(".*[0-9].*").matches(".*[@#$%&*!?].*");
        }
    }

    @Test
    void uploadedFileNamesAreStrippedOfPaths() {
        assertThat(DocumentService.sanitizeFileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.sanitizeFileName("C:\\Users\\x\\offer \"letter\".pdf")).isEqualTo("offer letter.pdf");
        assertThat(DocumentService.sanitizeFileName("   ")).isEqualTo("document");
    }
}
