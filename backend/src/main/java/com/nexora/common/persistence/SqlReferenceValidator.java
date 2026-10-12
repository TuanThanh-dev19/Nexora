package com.nexora.common.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.nexora.common.exception.ResourceNotFoundException;

@Component
public class SqlReferenceValidator {

    private final JdbcTemplate jdbcTemplate;

    public SqlReferenceValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireUser(Long userId) {
        requireExisting("dbo.app_users", "User", userId);
    }

    public void requireTicket(Long ticketId) {
        requireExisting("dbo.tickets", "Ticket", ticketId);
    }

    private void requireExisting(String table, String resourceName, Long id) {
        if (id == null) {
            throw new ResourceNotFoundException(resourceName + " id is required");
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException(resourceName + " " + id + " does not exist");
        }
    }
}
