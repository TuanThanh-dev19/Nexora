package com.nexora.ticket.entity;

/** Canonical work log classifications stored in SQL Server work_logs.log_type. */
public enum WorkLogType {
    DIAGNOSIS,
    PROGRESS,
    REPAIR,
    RESOLUTION,
    NO_FAULT_FOUND
}
