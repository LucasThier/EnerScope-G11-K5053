package org.enerscope.node.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Entity
@Setter
@NoArgsConstructor
// @AttributeOverride(name = "id", column = @Column(name = "VersionNodeId"))
public class ConnectionIdentity {

    public ConnectionIdentity(UUID id) {
        this.id = id;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;

    @Column(nullable = false)
    protected boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    @Column(name = "last_modified", nullable = false)
    protected Instant lastModified;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastModified = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModified = Instant.now();
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

}