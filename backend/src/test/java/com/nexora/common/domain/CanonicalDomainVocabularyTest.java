package com.nexora.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.nexora.asset.entity.AssetStatus;
import com.nexora.category.entity.DynamicFieldType;
import com.nexora.category.entity.FormStatus;
import com.nexora.location.entity.LocationType;
import com.nexora.ticket.entity.AttachmentType;
import com.nexora.ticket.entity.ResolutionOutcome;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserStatus;

class CanonicalDomainVocabularyTest {

    @Test
    void exposesOnlyCanonicalDomainValues() {
        assertThat(names(RoleCode.values())).containsExactly(
                "REQUESTER", "TECHNICIAN", "MANAGER", "ADMIN");
        assertThat(names(UserStatus.values())).containsExactly(
                "ACTIVE", "LOCKED", "DISABLED");
        assertThat(names(TicketStatus.values())).containsExactly(
                "SUBMITTED", "UNDER_REVIEW", "ASSIGNED", "IN_PROGRESS",
                "RESOLVED", "REOPENED", "CLOSED", "REJECTED", "CANCELLED");
        assertThat(names(ResolutionOutcome.values())).containsExactly(
                "FIXED", "TEMPORARY_FIX", "NO_FAULT_FOUND", "DUPLICATE", "UNRESOLVED");
        assertThat(names(DynamicFieldType.values())).containsExactly(
                "TEXT", "TEXTAREA", "NUMBER", "SELECT", "MULTI_SELECT",
                "DATE", "DATETIME", "BOOLEAN", "IMAGE");
        assertThat(names(AttachmentType.values())).containsExactly(
                "REPORT_EVIDENCE", "WORK_RESULT", "COMMENT");
        assertThat(names(AssetStatus.values())).containsExactly(
                "ACTIVE", "UNDER_MAINTENANCE", "OUT_OF_SERVICE", "RETIRED");
        assertThat(names(Priority.values())).containsExactly(
                "LOW", "NORMAL", "HIGH", "URGENT");
        assertThat(names(FormStatus.values())).containsExactly(
                "DRAFT", "PUBLISHED", "ARCHIVED");
        assertThat(names(LocationType.values())).containsExactly(
                "CAMPUS", "BUILDING", "FLOOR", "ROOM", "AREA");
    }

    @Test
    void applicationSourceDoesNotUseObsoleteRequesterRole() throws IOException {
        String obsoleteRole = String.join("", "US", "ER");
        Pattern obsoleteRoleToken = Pattern.compile("\\b" + obsoleteRole + "\\b");

        try (Stream<Path> sourceFiles = Files.walk(Path.of("src", "main", "java"))) {
            List<Path> offenders = sourceFiles
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, obsoleteRoleToken))
                    .toList();

            assertThat(offenders).isEmpty();
        }
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static boolean contains(Path path, Pattern pattern) {
        try {
            return pattern.matcher(Files.readString(path)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect source file " + path, exception);
        }
    }
}
