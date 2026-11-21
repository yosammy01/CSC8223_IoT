/**
 * JPA entity for persisted GLOBAL federated model snapshots only.
 * LOCAL client updates are not persisted and remain in memory.
 * See README.md for persistence and database configuration.
 */
package com.samuel_robles.java_backend.federated;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA entity mapped to the persisted global-model table. */
@Entity
@Table(name = "federated_model_snapshots")
/** Defines the FederatedModelSnapshot component. */
public class FederatedModelSnapshot {

    /** Stable primary key identifying the persisted snapshot. */
    @Id
    /** Stores the snapshotKey value. */
    private String snapshotKey;

    /** Snapshot category; this application persists the GLOBAL category. */
    private String modelType;

    /** Optional source client ID, normally null for a global snapshot. */
    private String clientId;

    /** Time when the global model was aggregated and saved. */
    private Instant updatedAt;

    /** Quantized 506-byte Android-compatible model payload. */
    @Column(columnDefinition = "bytea")
    @JdbcTypeCode(SqlTypes.VARBINARY)
    /** Stores the payload value. */
    private byte[] payload;

    /** Protected constructor required by JPA for entity materialization. */
    protected FederatedModelSnapshot() {
    }

    /** Creates an immutable-by-convention snapshot with a defensive payload copy. */
    public FederatedModelSnapshot(String snapshotKey, String modelType, String clientId,
            byte[] payload, Instant updatedAt) {
        this.snapshotKey = snapshotKey;
        this.modelType = modelType;
        this.clientId = clientId;
        this.payload = payload.clone();
        this.updatedAt = updatedAt;
    }

    /** Returns the primary snapshot key. */
    public String getSnapshotKey() {
        return snapshotKey;
    }

    /** Returns the snapshot category. */
    public String getModelType() {
        return modelType;
    }

    /** Returns the optional client source identifier. */
    public String getClientId() {
        return clientId;
    }

    /** Returns the time of the persisted aggregation. */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Returns a defensive copy of the quantized model payload. */
    public byte[] getPayload() {
        return payload.clone();
    }
}
