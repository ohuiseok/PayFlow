package com.payflow.transfer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Hibernate mapping for the table used directly by ShedLock's JDBC provider.
 */
@Entity
@Table(name = "shedlock")
public class ShedLock {

    @Id
    @Column(length = 64)
    private String name;

    @Column(nullable = false, columnDefinition = "TIMESTAMP(3)")
    private LocalDateTime lockUntil;

    @Column(nullable = false, columnDefinition = "TIMESTAMP(3)")
    private LocalDateTime lockedAt;

    @Column(nullable = false, length = 255)
    private String lockedBy;

    protected ShedLock() {
    }
}
