package com.doubleclick.wadii.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProviderPolicyVersionRepository extends JpaRepository<ProviderPolicyVersion, Long> {

    List<ProviderPolicyVersion> findByPolicyIdOrderByVersionNoDesc(Long policyId);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM ProviderPolicyVersion v WHERE v.policy.id = :policyId")
    int maxVersion(@Param("policyId") Long policyId);
}
