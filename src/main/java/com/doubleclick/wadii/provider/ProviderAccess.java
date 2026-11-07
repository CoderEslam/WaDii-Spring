package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the caller's provider profile for Provider Mode endpoints. The JWT identifies the user; nothing else. */
@Component
@RequiredArgsConstructor
public class ProviderAccess {

    private final ProviderRepository providers;
    private final SubscriptionService subscriptions;

    /** The caller's provider profile in any state (application screens). */
    @Transactional(readOnly = true)
    public Provider own(AuthUser user) {
        if (user.admin()) {
            throw BusinessException.of(ErrorCode.PROVIDER_NOT_ACTIVE, "Staff accounts cannot use Provider Mode.");
        }
        return providers.findByUserId(user.id()).orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_NOT_FOUND));
    }

    /** Provider Mode is open: verified and active. Used for managing the profile and existing orders. */
    @Transactional(readOnly = true)
    public Provider operating(AuthUser user) {
        Provider p = own(user);
        if (!p.canOperate()) {
            throw BusinessException.of(ErrorCode.PROVIDER_NOT_ACTIVE);
        }
        return p;
    }

    /** Operating and not restricted by an unpaid subscription. Required for new activity such as sending offers. */
    @Transactional(readOnly = true)
    public Provider forNewActivity(AuthUser user) {
        Provider p = operating(user);
        if (subscriptions.isRestricted(p.getId())) {
            throw BusinessException.of(ErrorCode.SUBSCRIPTION_RESTRICTED);
        }
        return p;
    }
}
