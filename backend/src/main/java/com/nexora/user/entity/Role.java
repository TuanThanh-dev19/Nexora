package com.nexora.user.entity;

import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles", schema = "dbo")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30, unique = true)
    private RoleCode code;

    @Column(nullable = false, length = 80)
    private String name;

    protected Role() {
    }

    public Role(RoleCode code, String name) {
        this.code = Objects.requireNonNull(code, "code is required");
        this.name = Objects.requireNonNull(name, "name is required");
    }

    public Short getId() {
        return id;
    }

    public RoleCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
