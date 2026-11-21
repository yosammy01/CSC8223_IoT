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

/** Spring Data JPA repository for global model snapshot persistence. */
public interface FederatedModelSnapshotRepository extends JpaRepository<FederatedModelSnapshot, String> {

    /** Finds a snapshot by its stable application-defined key. */
    Optional<FederatedModelSnapshot> findBySnapshotKey(String snapshotKey);

    /** Deletes snapshots belonging to a model category, such as legacy LOCAL records. */
    @Modifying
    @Transactional
    @Query("delete from FederatedModelSnapshot snapshot where snapshot.modelType = :modelType")
    int deleteByModelType(String modelType);
}
