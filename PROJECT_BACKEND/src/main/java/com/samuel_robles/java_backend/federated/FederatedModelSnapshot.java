package com.samuel_robles.java_backend.federated;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "federated_model_snapshots")
public class FederatedModelSnapshot {

    @Id
    private String snapshotKey;

    private String modelType;
    private String clientId;
    private Instant updatedAt;

    @Lob
    private byte[] payload;

    protected FederatedModelSnapshot() {
    }

    public FederatedModelSnapshot(String snapshotKey, String modelType, String clientId,
            byte[] payload, Instant updatedAt) {
        this.snapshotKey = snapshotKey;
        this.modelType = modelType;
        this.clientId = clientId;
        this.payload = payload.clone();
        this.updatedAt = updatedAt;
    }

    public String getSnapshotKey() {
        return snapshotKey;
    }

    public String getModelType() {
        return modelType;
    }

    public String getClientId() {
        return clientId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public byte[] getPayload() {
        return payload.clone();
    }
}
