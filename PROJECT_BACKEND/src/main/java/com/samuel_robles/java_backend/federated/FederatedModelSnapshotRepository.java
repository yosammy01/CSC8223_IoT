/**
 * Spring Data repository for federated model snapshots.
 * See README.md for the database-backed model lifecycle.
 */
package com.samuel_robles.java_backend.federated;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface FederatedModelSnapshotRepository extends JpaRepository<FederatedModelSnapshot, String> {

    Optional<FederatedModelSnapshot> findBySnapshotKey(String snapshotKey);

    @Modifying
    @Transactional
    @Query("delete from FederatedModelSnapshot snapshot where snapshot.modelType = :modelType")
    int deleteByModelType(String modelType);
}
