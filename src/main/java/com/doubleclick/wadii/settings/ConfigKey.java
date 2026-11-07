package com.doubleclick.wadii.settings;

import com.doubleclick.wadii.settings.Configs.CommissionConfig;
import com.doubleclick.wadii.settings.Configs.MarketplaceConfig;
import com.doubleclick.wadii.settings.Configs.SellerConfig;
import com.doubleclick.wadii.settings.Configs.SubscriptionConfig;

/** Admin-configurable groups. Financial groups may only be changed by finance admins. */
public enum ConfigKey {
    MARKETPLACE(MarketplaceConfig.class, false),
    COMMISSION(CommissionConfig.class, true),
    SUBSCRIPTION(SubscriptionConfig.class, true),
    SELLER(SellerConfig.class, true);

    private final Class<? extends Configs.Validated> type;
    private final boolean financial;

    ConfigKey(Class<? extends Configs.Validated> type, boolean financial) {
        this.type = type;
        this.financial = financial;
    }

    public Class<? extends Configs.Validated> type() {
        return type;
    }

    public boolean financial() {
        return financial;
    }
}
