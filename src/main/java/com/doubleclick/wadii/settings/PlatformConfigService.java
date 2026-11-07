package com.doubleclick.wadii.settings;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.identity.AdminRole;
import com.doubleclick.wadii.settings.Configs.CommissionConfig;
import com.doubleclick.wadii.settings.Configs.MarketplaceConfig;
import com.doubleclick.wadii.settings.Configs.SellerConfig;
import com.doubleclick.wadii.settings.Configs.SubscriptionConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Reads and versions admin configuration. Services always read the version in force "now"; anything that must
 * not change afterwards (commission on an offer, policy on an order) stores a snapshot plus the version id.
 */
@Service
@RequiredArgsConstructor
public class PlatformConfigService {

    private final ConfigVersionRepository repository;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final Clock clock;

    /** A typed configuration together with the id of the version it came from. */
    public record Versioned<T>(Long versionId, T value) {
    }

    public record VersionView(Long id, ConfigKey key, JsonNode payload, Instant effectiveFrom, Long createdByUserId,
                              String reason, Instant createdAt) {
    }

    public MarketplaceConfig marketplace() {
        return current(ConfigKey.MARKETPLACE, MarketplaceConfig.class).value();
    }

    public Versioned<CommissionConfig> commission() {
        return current(ConfigKey.COMMISSION, CommissionConfig.class);
    }

    public Versioned<SubscriptionConfig> subscription() {
        return current(ConfigKey.SUBSCRIPTION, SubscriptionConfig.class);
    }

    public Versioned<SellerConfig> seller() {
        return current(ConfigKey.SELLER, SellerConfig.class);
    }

    @Transactional(readOnly = true)
    public <T> Versioned<T> current(ConfigKey key, Class<T> type) {
        ConfigVersion version = inForce(key);
        return new Versioned<>(version.getId(), parse(version.getPayload(), type));
    }

    @Transactional(readOnly = true)
    public VersionView currentView(ConfigKey key) {
        return view(inForce(key));
    }

    @Transactional(readOnly = true)
    public List<VersionView> history(ConfigKey key) {
        return repository.findByConfigKeyOrderByEffectiveFromDescIdDesc(key).stream().map(this::view).toList();
    }

    @Transactional
    public VersionView publish(AuthUser admin, ConfigKey key, JsonNode payload, Instant effectiveFrom, String reason) {
        if (key.financial() && !admin.hasAdminRole(AdminRole.FINANCE_ADMIN)) {
            throw BusinessException.forbidden("Financial settings require the finance admin role.");
        }
        if (reason == null || reason.isBlank()) {
            throw BusinessException.validation("A reason is required for every configuration change.");
        }
        Configs.Validated typed;
        try {
            typed = strict().treeToValue(payload, key.type());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw BusinessException.of(ErrorCode.CONFIG_INVALID, "The payload does not match the " + key + " configuration.");
        }
        typed.validate();
        Instant now = Instant.now(clock);
        ConfigVersion version = new ConfigVersion();
        version.setConfigKey(key);
        version.setPayload(write(typed));
        version.setEffectiveFrom(effectiveFrom == null || effectiveFrom.isBefore(now) ? now : effectiveFrom);
        version.setCreatedBy(admin.id());
        version.setReason(reason.trim());
        repository.save(version);
        audit.record("CONFIG_CHANGED", "CONFIG_" + key, version.getId(), version.getPayload());
        return view(version);
    }

    private ConfigVersion inForce(ConfigKey key) {
        return repository.inForce(key, Instant.now(clock), PageRequest.of(0, 1)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No configuration in force for " + key));
    }

    private VersionView view(ConfigVersion v) {
        try {
            return new VersionView(v.getId(), v.getConfigKey(), objectMapper.readTree(v.getPayload()), v.getEffectiveFrom(),
                    v.getCreatedBy(), v.getReason(), v.getCreatedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored configuration is not valid JSON: " + v.getId(), e);
        }
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored configuration does not match " + type.getSimpleName(), e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Admin input must not silently drop unknown fields or default missing primitives to zero. */
    private ObjectMapper strict() {
        return objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
    }
}
