package com.doubleclick.wadii.seed;

import com.doubleclick.wadii.catalog.CategoryRepository;
import com.doubleclick.wadii.catalog.ServiceCategory;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.listing.ListingCategory;
import com.doubleclick.wadii.listing.ListingCondition;
import com.doubleclick.wadii.listing.Listing;
import com.doubleclick.wadii.listing.ListingService;
import com.doubleclick.wadii.offer.OfferService;
import com.doubleclick.wadii.offer.OfferViews;
import com.doubleclick.wadii.provider.BranchHours;
import com.doubleclick.wadii.provider.BranchHoursRepository;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderBranch;
import com.doubleclick.wadii.provider.ProviderBranchRepository;
import com.doubleclick.wadii.provider.ProviderCategory;
import com.doubleclick.wadii.provider.ProviderCategoryRepository;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.request.RequestService;
import com.doubleclick.wadii.request.ServiceItem;
import com.doubleclick.wadii.request.ServiceItemRepository;
import com.doubleclick.wadii.request.ServiceRequest;
import com.doubleclick.wadii.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Demo data for local development only ({@code dev} profile). Providers are inserted already verified so the
 * marketplace has something to show; every other step goes through the real services. All passwords: Password123.
 */
@Slf4j
@Component
@Profile("dev")
@Order(10)
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private static final String PASSWORD = "Password123";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ProviderRepository providers;
    private final ProviderCategoryRepository providerCategories;
    private final ProviderBranchRepository branches;
    private final BranchHoursRepository hours;
    private final CategoryRepository categories;
    private final ServiceItemRepository items;
    private final SubscriptionService subscriptions;
    private final RequestService requests;
    private final OfferService offers;
    private final ListingService listings;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase("customer@wadii.dev")) {
            return;
        }
        Map<String, ServiceCategory> byCode = categories.findAll().stream()
                .collect(Collectors.toMap(ServiceCategory::getCode, Function.identity()));

        User customer = user("Mona Adel", "customer@wadii.dev", "01011111111");
        User seller = user("Karim Saleh", "seller@wadii.dev", "01022222222");
        User p1 = user("Ahmed Fathy", "provider1@wadii.dev", "01033333333");
        User p2 = user("Hany Morsy", "provider2@wadii.dev", "01044444444");
        user("Sara Nabil", "provider3@wadii.dev", "01055555555");

        Provider batteries = provider(p1, "Fathy Auto Electric", "Batteries, diagnostics and electrical repairs.",
                List.of(byCode.get("BATTERIES"), byCode.get("OIL_CHANGE"), byCode.get("REPAIRS")), "Nasr City, Makram Ebeid St.");
        Provider tires = provider(p2, "Morsy Tires & Wash", "Tires, balancing and hand car wash.",
                List.of(byCode.get("TIRES"), byCode.get("CAR_WASH"), byCode.get("OIL_CHANGE")), "Heliopolis, El Merghany St.");

        AuthUser customerAuth = auth(customer);
        ServiceRequest request = requests.create(customerAuth, new RequestService.RequestInput(
                new RequestService.VehicleChoice(null, "Hyundai", "Elantra", 2019, true),
                List.of(new RequestService.ItemInput(byCode.get("BATTERIES").getId(), "Car does not start in the morning"),
                        new RequestService.ItemInput(byCode.get("OIL_CHANGE").getId(), "Synthetic 5W-30 please")),
                1L, "Nasr City, Abbas El Akkad", null, null, null, null, null, "Available after 4 pm", List.of(), null));
        Map<Long, Long> itemByCategory = items.findByRequestIdOrderByIdAsc(request.getId()).stream()
                .collect(Collectors.toMap(ServiceItem::getCategoryId, ServiceItem::getId));
        offer(p1, batteries, request, List.of(
                line(itemByCategory.get(byCode.get("BATTERIES").getId()), "1900", true),
                line(itemByCategory.get(byCode.get("OIL_CHANGE").getId()), "650", false)));
        offer(p2, tires, request, List.of(line(itemByCategory.get(byCode.get("OIL_CHANGE").getId()), "590", false)));

        AuthUser sellerAuth = auth(seller);
        var listing = listings.create(sellerAuth, new ListingService.ListingInput("Original Toyota alloy wheels (set of 4)",
                ListingCategory.ACCESSORIES, ListingCondition.GOOD, "15 inch, minor scratches, no bends.", new BigDecimal("6500"),
                1L, "Maadi", Listing.ContactMethod.CHAT, List.of()));
        listings.submit(sellerAuth, listing.id());
        listings.approve(auth(users.findByEmailIgnoreCase(adminEmail()).orElse(seller)), listing.id());

        log.info("Dev data seeded: customer@wadii.dev, seller@wadii.dev, provider1..3@wadii.dev (password {})", PASSWORD);
    }

    private String adminEmail() {
        return users.findAll().stream().filter(User::isAdmin).map(User::getEmail).findFirst().orElse("");
    }

    private User user(String name, String email, String phone) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setPhone(phone);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return users.save(u);
    }

    private Provider provider(User owner, String name, String description, List<ServiceCategory> offered, String address) {
        Instant now = Instant.now(clock);
        Provider p = new Provider();
        p.setUserId(owner.getId());
        p.setBusinessName(name);
        p.setDescription(description);
        p.setPhone("0224000000");
        p.setAreaId(1L);
        p.setAddress(address);
        p.setApplicationStatus(Provider.ApplicationStatus.VERIFIED);
        p.setSubmittedAt(now);
        p.setReviewedAt(now);
        p.setVerifiedAt(now);
        providers.save(p);
        offered.forEach(c -> providerCategories.save(new ProviderCategory(p.getId(), c.getId())));
        ProviderBranch b = new ProviderBranch();
        b.setProviderId(p.getId());
        b.setName("Main branch");
        b.setAreaId(1L);
        b.setAddress(address);
        b.setPhone("0224000000");
        b.setSlotMinutes(60);
        b.setMaxBookingsPerSlot(3);
        branches.save(b);
        for (int day : List.of(1, 2, 3, 4, 6, 7)) {
            hours.save(new BranchHours(b.getId(), day, LocalTime.of(9, 0), LocalTime.of(21, 0)));
        }
        subscriptions.ensure(p.getId());
        return p;
    }

    private void offer(User owner, Provider provider, ServiceRequest request, List<OfferViews.LineInput> lines) {
        Long branchId = branches.findByProviderIdOrderByIdAsc(provider.getId()).get(0).getId();
        offers.submit(auth(owner), request.getId(), new OfferViews.OfferInput(branchId, 24, "Can start today.", lines));
    }

    private static OfferViews.LineInput line(Long itemId, String price, boolean warranty) {
        return new OfferViews.LineInput(itemId, true, new BigDecimal(price), new BigDecimal("100"), BigDecimal.ZERO,
                List.of(), 60, null, null,
                warranty ? new OfferViews.Warranty(true, 365, com.doubleclick.wadii.offer.OfferLine.WarrantyCoverage.PARTS, null, null)
                        : new OfferViews.Warranty(false, null, null, null, null));
    }

    private static AuthUser auth(User u) {
        return new AuthUser(u.getId(), u.getEmail(), u.getFullName(), u.isAdmin(), u.isAdmin()
                ? EnumSet.allOf(com.doubleclick.wadii.identity.AdminRole.class) : EnumSet.noneOf(com.doubleclick.wadii.identity.AdminRole.class));
    }
}
