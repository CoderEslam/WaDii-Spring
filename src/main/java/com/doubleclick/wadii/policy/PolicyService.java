package com.doubleclick.wadii.policy;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider policies with append-only versions, platform rules and order snapshots.
 * Precedence: legal/platform rules -> provider policy -> (product policy, reserved) -> order snapshot.
 */
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final ProviderPolicyRepository policyRepository;
    private final ProviderPolicyVersionRepository versionRepository;
    private final OrderPolicySnapshotRepository snapshotRepository;
    private final ProviderAccess providerAccess;
    private final AuditService auditService;
    private final WadiiProperties properties;

    public record PolicyTerms(PolicyType type, PolicySource source, Long versionId, Integer versionNo, String terms,
                              Integer periodDays, BigDecimal feePercent, Integer freeCancellationHours) {
    }

    public record PolicyVersionRequest(String terms, Integer periodDays, BigDecimal feePercent, Integer freeCancellationHours) {
    }

    public record SnapshotView(PolicyType type, PolicySource source, Long providerPolicyVersionId, Integer versionNo,
                               String terms, Integer periodDays, BigDecimal feePercent, Integer freeCancellationHours,
                               java.time.Instant createdAt) {
        static SnapshotView of(OrderPolicySnapshot s) {
            return new SnapshotView(s.getPolicyType(), s.getSource(), s.getProviderPolicyVersionId(), s.getVersionNo(),
                    s.getTerms(), s.getPeriodDays(), s.getFeePercent(), s.getFreeCancellationHours(), s.getCreatedAt());
        }
    }

    /** Platform defaults used when a provider has not published a policy of that type. */
    public PolicyTerms platformDefault(PolicyType type) {
        WadiiProperties.PlatformPolicy rules = properties.getPlatformPolicy();
        return switch (type) {
            case RETURN -> new PolicyTerms(type, PolicySource.PLATFORM, null, null,
                    "Unused spare parts can be returned within 14 days of delivery in their original condition.",
                    Math.max(14, rules.getMinRefundWindowDays()), null, null);
            case REFUND -> new PolicyTerms(type, PolicySource.PLATFORM, null, null,
                    "Refunds are paid back with the original payment method within 14 days of approval.",
                    Math.max(14, rules.getMinRefundWindowDays()), null, null);
            case CANCELLATION -> new PolicyTerms(type, PolicySource.PLATFORM, null, null,
                    "Free cancellation until 24 hours before the appointment. Later cancellations are free in V1.",
                    null, BigDecimal.ZERO, 24);
            case WARRANTY -> new PolicyTerms(type, PolicySource.PLATFORM, null, null,
                    "No warranty unless the provider publishes one.", rules.getMinWarrantyDays(), null, null);
            case PAYMENT -> new PolicyTerms(type, PolicySource.PLATFORM, null, null,
                    "The accepted price is paid before the service starts, by Vodafone Cash, InstaPay or cash.", null, null, null);
            case SERVICE -> new PolicyTerms(type, PolicySource.PLATFORM, null, null,
                    "The provider performs the services described in the accepted offer. Extra work needs the customer's approval.",
                    null, null, null);
            case NO_SHOW -> new PolicyTerms(type, PolicySource.PLATFORM, null, null,
                    "A no-show is reported after the appointment time plus a grace period and may cancel the order.",
                    null, BigDecimal.ZERO, null);
        };
    }

    /** Rejects provider terms that weaken mandatory platform rules. */
    void validateAgainstPlatform(PolicyType type, PolicyVersionRequest r) {
        WadiiProperties.PlatformPolicy rules = properties.getPlatformPolicy();
        if (r.terms() == null || r.terms().isBlank() || r.terms().length() > 10000) {
            throw BusinessException.validation("terms are required (max 10000 characters)");
        }
        if (r.periodDays() != null && r.periodDays() < 0) throw BusinessException.validation("periodDays must be >= 0");
        if (r.freeCancellationHours() != null && r.freeCancellationHours() < 0) throw BusinessException.validation("freeCancellationHours must be >= 0");
        if (r.feePercent() != null && (r.feePercent().signum() < 0 || r.feePercent().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw BusinessException.validation("feePercent must be between 0 and 100");
        }
        List<String> violations = new ArrayList<>();
        switch (type) {
            case CANCELLATION -> {
                if (r.feePercent() != null && r.feePercent().compareTo(rules.getMaxCancellationFeePercent()) > 0)
                    violations.add("cancellation fee may not exceed " + rules.getMaxCancellationFeePercent() + "%");
            }
            case NO_SHOW -> {
                if (r.feePercent() != null && r.feePercent().compareTo(rules.getMaxNoShowFeePercent()) > 0)
                    violations.add("no-show fee may not exceed " + rules.getMaxNoShowFeePercent() + "%");
            }
            case WARRANTY -> {
                int days = r.periodDays() == null ? 0 : r.periodDays();
                if (days < rules.getMinWarrantyDays()) violations.add("warranty must be at least " + rules.getMinWarrantyDays() + " days");
            }
            case RETURN, REFUND -> {
                int days = r.periodDays() == null ? 0 : r.periodDays();
                if (days < rules.getMinRefundWindowDays())
                    violations.add(type.name().toLowerCase() + " window must be at least " + rules.getMinRefundWindowDays() + " days");
            }
            default -> {
            }
        }
        if (!violations.isEmpty()) {
            throw BusinessException.of(ErrorCode.POLICY_VIOLATES_PLATFORM_RULES, String.join("; ", violations));
        }
    }

    /** Never overwrites: every change creates version N+1 and moves the current pointer. */
    @Transactional
    public PolicyTerms publishVersion(AuthUser me, PolicyType type, PolicyVersionRequest request) {
        Provider provider = providerAccess.requireOwn(me);
        validateAgainstPlatform(type, request);
        ProviderPolicy policy = policyRepository.findByProviderIdAndPolicyType(provider.getId(), type).orElseGet(() -> {
            ProviderPolicy p = new ProviderPolicy();
            p.setProviderId(provider.getId());
            p.setPolicyType(type);
            return policyRepository.save(p);
        });
        ProviderPolicyVersion v = new ProviderPolicyVersion();
        v.setPolicy(policy);
        v.setVersionNo(versionRepository.maxVersion(policy.getId()) + 1);
        v.setTerms(request.terms().trim());
        v.setPeriodDays(request.periodDays());
        v.setFeePercent(request.feePercent());
        v.setFreeCancellationHours(request.freeCancellationHours());
        v.setCreatedBy(me.id());
        v = versionRepository.save(v);
        policy.setCurrentVersion(v);
        auditService.record(me, ActorRole.PROVIDER, AuditAction.POLICY_VERSION_CREATED, "PROVIDER_POLICY", policy.getId(),
                type + " v" + v.getVersionNo());
        return toTerms(type, v);
    }

    /** Effective policy of every type for a provider (provider version, else platform default). */
    @Transactional(readOnly = true)
    public List<PolicyTerms> effectivePolicies(Long providerId) {
        Map<PolicyType, PolicyTerms> result = new EnumMap<>(PolicyType.class);
        for (PolicyType t : PolicyType.values()) result.put(t, platformDefault(t));
        for (ProviderPolicy p : policyRepository.findByProviderId(providerId)) {
            if (p.getCurrentVersion() != null) result.put(p.getPolicyType(), toTerms(p.getPolicyType(), p.getCurrentVersion()));
        }
        return new ArrayList<>(result.values());
    }

    @Transactional(readOnly = true)
    public List<PolicyTerms> history(AuthUser me, PolicyType type) {
        Provider provider = providerAccess.requireOwn(me);
        Optional<ProviderPolicy> policy = policyRepository.findByProviderIdAndPolicyType(provider.getId(), type);
        return policy.map(p -> versionRepository.findByPolicyIdOrderByVersionNoDesc(p.getId()).stream()
                .map(v -> toTerms(type, v)).toList()).orElse(List.of());
    }

    /** Immutable copy of every effective policy, taken inside the accept transaction. Idempotent per order. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void snapshot(Long orderId, Long providerId) {
        if (snapshotRepository.existsByOrderId(orderId)) return;
        for (PolicyTerms t : effectivePolicies(providerId)) {
            OrderPolicySnapshot s = new OrderPolicySnapshot();
            s.setOrderId(orderId);
            s.setPolicyType(t.type());
            s.setSource(t.source());
            s.setProviderPolicyVersionId(t.versionId());
            s.setVersionNo(t.versionNo());
            s.setTerms(t.terms());
            s.setPeriodDays(t.periodDays());
            s.setFeePercent(t.feePercent());
            s.setFreeCancellationHours(t.freeCancellationHours());
            snapshotRepository.save(s);
        }
    }

    @Transactional(readOnly = true)
    public List<SnapshotView> snapshotsOf(Long orderId) {
        return snapshotRepository.findByOrderIdOrderByPolicyType(orderId).stream().map(SnapshotView::of).toList();
    }

    /** The snapshot of one type; falls back to the platform default when the order has no snapshot (not accepted yet). */
    @Transactional(readOnly = true)
    public PolicyTerms snapshotTerms(Long orderId, PolicyType type) {
        return snapshotRepository.findByOrderIdAndPolicyType(orderId, type)
                .map(s -> new PolicyTerms(type, s.getSource(), s.getProviderPolicyVersionId(), s.getVersionNo(), s.getTerms(),
                        s.getPeriodDays(), s.getFeePercent(), s.getFreeCancellationHours()))
                .orElseGet(() -> platformDefault(type));
    }

    private PolicyTerms toTerms(PolicyType type, ProviderPolicyVersion v) {
        return new PolicyTerms(type, PolicySource.PROVIDER, v.getId(), v.getVersionNo(), v.getTerms(), v.getPeriodDays(),
                v.getFeePercent(), v.getFreeCancellationHours());
    }
}
