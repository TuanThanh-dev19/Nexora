package com.nexora.report.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Component;

import com.nexora.ticket.dto.response.TicketSummaryResponse;

@Component
public class TicketCsvExporter {

    private static final String[] HEADERS = {
            "Mã phiếu",
            "Tiêu đề",
            "Trạng thái",
            "Mức ưu tiên",
            "Vị trí",
            "Danh mục",
            "Người tạo",
            "Hạn SLA",
            "Ngày tạo",
            "Ngày cập nhật"
    };

    public byte[] export(List<TicketSummaryResponse> tickets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            // Write UTF-8 BOM for Excel compatibility
            writer.print('\uFEFF');

            // Write Header
            for (int i = 0; i < HEADERS.length; i++) {
                writer.print(escapeCsv(HEADERS[i]));
                if (i < HEADERS.length - 1) {
                    writer.print(",");
                }
            }
            writer.print("\r\n");

            // Write rows
            for (TicketSummaryResponse ticket : tickets) {
                writer.print(escapeCsv(ticket.ticketNumber()));
                writer.print(",");
                writer.print(escapeCsv(ticket.title()));
                writer.print(",");
                writer.print(escapeCsv(ticket.status() != null ? ticket.status().name() : ""));
                writer.print(",");
                writer.print(escapeCsv(ticket.priority() != null ? ticket.priority().name() : ""));
                writer.print(",");
                writer.print(escapeCsv(ticket.location() != null ? ticket.location().name() : ""));
                writer.print(",");
                writer.print(escapeCsv(ticket.category() != null ? ticket.category().name() : ""));
                writer.print(",");
                writer.print(escapeCsv(ticket.reporter() != null ? ticket.reporter().fullName() : ""));
                writer.print(",");
                writer.print(escapeCsv(ticket.slaDueAt() != null ? ticket.slaDueAt().toString() : ""));
                writer.print(",");
                writer.print(escapeCsv(ticket.createdAt() != null ? ticket.createdAt().toString() : ""));
                writer.print(",");
                writer.print(escapeCsv(ticket.updatedAt() != null ? ticket.updatedAt().toString() : ""));
                writer.print("\r\n");
            }

            writer.flush();
            return out.toByteArray();
        }
    }

    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        String safeValue = value;
        // Formula injection defense: deactivate formulas starting with =, +, -, @, or control characters
        if (!safeValue.isEmpty()) {
            char firstChar = safeValue.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@'
                    || firstChar == '\t' || firstChar == '\r' || firstChar <= 31) {
                safeValue = "'" + safeValue;
            }
        }

        // Escape if contains comma, quote, newline, or leading single quote
        boolean needsQuotes = safeValue.contains(",")
                || safeValue.contains("\"")
                || safeValue.contains("\n")
                || safeValue.contains("\r")
                || safeValue.startsWith("'");

        if (needsQuotes) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }
}

