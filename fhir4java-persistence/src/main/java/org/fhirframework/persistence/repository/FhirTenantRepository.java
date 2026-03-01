package org.fhirframework.persistence.repository;

import org.fhirframework.persistence.entity.FhirTenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for tenant mapping operations.
 */
@Repository
public interface FhirTenantRepository extends JpaRepository<FhirTenantEntity, Long> {

    Optional<FhirTenantEntity> findByExternalId(UUID externalId);

    Optional<FhirTenantEntity> findByInternalId(String internalId);

    Optional<FhirTenantEntity> findByTenantCode(String tenantCode);

    List<FhirTenantEntity> findByEnabledTrue();

    boolean existsByExternalId(UUID externalId);

    boolean existsByInternalId(String internalId);
}
