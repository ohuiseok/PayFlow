package com.payflow.reward.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "parent_child_links",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_parent_child_links_pair", columnNames = {"parentUserId", "childUserId"})
        }
)
public class ParentChildLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parentUserId;

    @Column(nullable = false)
    private Long childUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParentChildLinkStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ParentChildLink() {
    }

    public ParentChildLink(Long parentUserId, Long childUserId) {
        this.parentUserId = parentUserId;
        this.childUserId = childUserId;
        this.status = ParentChildLinkStatus.ACTIVE;
    }

    public void activate() {
        this.status = ParentChildLinkStatus.ACTIVE;
    }

    public void delete() {
        this.status = ParentChildLinkStatus.DELETED;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getParentUserId() {
        return parentUserId;
    }

    public Long getChildUserId() {
        return childUserId;
    }

    public ParentChildLinkStatus getStatus() {
        return status;
    }
}
