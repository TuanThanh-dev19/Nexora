package com.nexora.sla.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.sla.entity.SlaPolicy;

/**
 * Spring Data repository for {@link SlaPolicy}.
 *
 * <p>SLA policies are reference data. Mutations are performed through a
 * dedicated service, not via direct {@link #save} calls.
 */
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    List<SlaPolicy> findByActiveTrueOrderByPolicyNameAscIdAsc();

    /**
     * Returns all active SLA policies ordered by policy name.
     *
     * @return active policies, ordered alphabetically by name
     */
    List<SlaPolicy> findByActiveTrueOrderByPolicyName();

    /**
     * Looks up a policy by its unique display name.
     *
     * @param policyName the unique policy name
     * @return the policy, or empty if not found
     */
    Optional<SlaPolicy> findByPolicyName(String policyName);
}
