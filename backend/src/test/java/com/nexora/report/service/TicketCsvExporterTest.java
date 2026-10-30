package com.nexora.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.common.domain.Priority;
import com.nexora.ticket.dto.response.TicketSummaryResponse;
import com.nexora.ticket.dto.response.TicketSummaryResponse.CatalogReference;
import com.nexora.ticket.dto.response.TicketSummaryResponse.UserReference;
import com.nexora.ticket.entity.TicketStatus;

class TicketCsvExporterTest {

    private final TicketCsvExporter exporter = new TicketCsvExporter();

    @Test
    @DisplayName("export: writes UTF-8 BOM, headers, and preserves Vietnamese characters")
    void export_utf8Bom_headers_andVietnameseText() {
        TicketSummaryResponse ticket = new TicketSummaryResponse(
                1L,
                "TK-2026-0001",
                "Máy chiếu phòng H101 bị hỏng bóng đèn",
                TicketStatus.IN_PROGRESS,
                Priority.HIGH,
                new CatalogReference(10L, "HARDWARE", "Thiết bị phần cứng"),
                new CatalogReference(20L, "H101", "Phòng họp H101"),
                null,
                new UserReference(5L, "Nguyễn Văn A"),
                OffsetDateTime.parse("2026-10-30T15:00:00+07:00"),
                OffsetDateTime.parse("2026-10-30T09:00:00+07:00"),
                OffsetDateTime.parse("2026-10-30T10:00:00+07:00"),
                null
        );

        byte[] bytes = exporter.export(List.of(ticket));

        // Check BOM
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);

        String csvString = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csvString).startsWith("\uFEFFMã phiếu,Tiêu đề,Trạng thái,Mức ưu tiên,Vị trí,Danh mục,Người tạo,Hạn SLA,Ngày tạo,Ngày cập nhật\r\n");
        assertThat(csvString).contains("Máy chiếu phòng H101 bị hỏng bóng đèn");
        assertThat(csvString).contains("Nguyễn Văn A");
        assertThat(csvString).contains("Phòng họp H101");
        assertThat(csvString).contains("TK-2026-0001");
    }

    @Test
    @DisplayName("export: escapes commas, quotes, and newlines properly")
    void export_escapesCommasQuotesAndNewlines() {
        TicketSummaryResponse ticket = new TicketSummaryResponse(
                2L,
                "TK-2026-0002",
                "Lỗi \"nghiêm trọng\", cần xử lý ngay:\n- Mất điện\r\n- Chập cháy",
                TicketStatus.SUBMITTED,
                Priority.URGENT,
                new CatalogReference(10L, "ELEC", "Điện, nước"),
                new CatalogReference(20L, "F1, Tầng 1", "Tòa F, P102"),
                null,
                new UserReference(5L, "Trần \"Bảo\" Nam"),
                null,
                OffsetDateTime.parse("2026-10-30T09:00:00+07:00"),
                OffsetDateTime.parse("2026-10-30T09:00:00+07:00"),
                null
        );

        byte[] bytes = exporter.export(List.of(ticket));
        String csvString = new String(bytes, StandardCharsets.UTF_8);

        // Quotes inside string should be doubled and whole string enclosed in quotes
        assertThat(csvString).contains("\"Lỗi \"\"nghiêm trọng\"\", cần xử lý ngay:\n- Mất điện\r\n- Chập cháy\"");
        assertThat(csvString).contains("\"Điện, nước\"");
        assertThat(csvString).contains("\"Tòa F, P102\"");
        assertThat(csvString).contains("\"Trần \"\"Bảo\"\" Nam\"");
    }

    @Test
    @DisplayName("export: deactivates dangerous formula characters (=, +, -, @)")
    void export_deactivatesFormulaInjection() {
        List<String> dangerousTitles = List.of(
                "=cmd|' /C calc'!A0",
                "+12345",
                "-2+5",
                "@SUM(1,2)",
                "\tDANGEROUS"
        );

        for (int i = 0; i < dangerousTitles.size(); i++) {
            TicketSummaryResponse ticket = new TicketSummaryResponse(
                    (long) (i + 1),
                    "TK-" + i,
                    dangerousTitles.get(i),
                    TicketStatus.SUBMITTED,
                    Priority.LOW,
                    null,
                    null,
                    null,
                    null,
                    null,
                    OffsetDateTime.parse("2026-10-30T09:00:00+07:00"),
                    OffsetDateTime.parse("2026-10-30T09:00:00+07:00"),
                    null
            );

            byte[] bytes = exporter.export(List.of(ticket));
            String csvString = new String(bytes, StandardCharsets.UTF_8);

            // Each dangerous title must be deactivated with single quote prefix
            char firstChar = dangerousTitles.get(i).charAt(0);
            assertThat(csvString).contains("'" + dangerousTitles.get(i));
        }
    }

    @Test
    @DisplayName("export: empty list returns only header with BOM")
    void export_emptyList_returnsHeaderOnly() {
        byte[] bytes = exporter.export(List.of());
        String csvString = new String(bytes, StandardCharsets.UTF_8);

        assertThat(csvString).isEqualTo("\uFEFFMã phiếu,Tiêu đề,Trạng thái,Mức ưu tiên,Vị trí,Danh mục,Người tạo,Hạn SLA,Ngày tạo,Ngày cập nhật\r\n");
    }
}
