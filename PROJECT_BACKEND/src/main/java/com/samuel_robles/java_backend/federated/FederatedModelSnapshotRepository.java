package com.samuel_robles.java_backend.federated;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FederatedModelSnapshotRepository extends JpaRepository<FederatedModelSnapshot, String> {

    Optional<FederatedModelSnapshot> findBySnapshotKey(String snapshotKey);

    List<FederatedModelSnapshot> findByModelType(String modelType);
}
