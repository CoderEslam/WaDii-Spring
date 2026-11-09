package com.doubleclick.wadii.listing;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.util.KeyList;
import com.doubleclick.wadii.common.util.Money;
import com.doubleclick.wadii.common.util.Times;
import com.doubleclick.wadii.files.FilePurpose;
import com.doubleclick.wadii.files.FileService;
import com.doubleclick.wadii.fraud.FraudService;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.ledger.LedgerEntry;
import com.doubleclick.wadii.ledger.LedgerRepository;
import com.doubleclick.wadii.ledger.LedgerService;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.settings.Configs;
import com.doubleclick.wadii.settings.Configs.SellerConfig;
import com.doubleclick.wadii.settings.PlatformConfigService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Individual seller listings. The quota counts listings in review or active (sold or closed ones free a slot).
 * The seller fee, when enabled, is recorded as an amount due at publication or at sale, as configured; it is never
 * treated as collected until finance settles it.
 */
@Service
@RequiredArgsConstructor
public class ListingService {

    static final Set<Listing.Status> QUOTA = EnumSet.of(Listing.Status.PENDING_REVIEW, Listing.Status.ACTIVE);

    private final ListingRepository listings;
    private final UserRepository users;
    private final CatalogService catalog;
    private final FileService files;
    private final LedgerService ledger;
    private final LedgerRepository ledgerRepository;
    private final PlatformConfigService config;
    private final FraudService fraud;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public record ListingInput(@NotBlank @Size(min = 3, max = 120) String title, @NotNull ListingCategory category,
                               @NotNull ListingCondition condition, @NotBlank @Size(max = 4000) String description,
                               @NotNull @DecimalMin("1") @Digits(integer = 10, fraction = 2) BigDecimal price,
                               @NotNull Long areaId, @Size(max = 200) String locationText,
                               @NotNull Listing.ContactMethod contactMethod, @Size(max = 8) List<String> photoKeys) {
    }

    public record ListingView(Long id, Long sellerId, String sellerName, String title, ListingCategory category,
                              ListingCondition condition, String description, BigDecimal price, Long areaId, String locationText,
                              Listing.ContactMethod contactMethod, String phone, List<String> photoKeys, Listing.Status status,
                              String rejectionReason, Instant publishedAt, Instant expiresAt, Instant createdAt, boolean mine) {
    }

    public record SellerSummary(long activeListings, int maxActiveListings, BigDecimal feesDue, List<LedgerService.View> fees) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingView> browse(AuthUser viewer, ListingCategory category, Long areaId, String q, BigDecimal minPrice,
                                            BigDecimal maxPrice, Integer page, Integer size) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return views(listings.browse(category, areaId, pattern, minPrice, maxPrice, PageResponse.pageable(page, size)), viewer);
    }

    @Transactional(readOnly = true)
    public ListingView detail(AuthUser viewer, Long id) {
        Listing l = listings.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.LISTING_NOT_FOUND));
        boolean visible = l.getStatus() == Listing.Status.ACTIVE || l.getStatus() == Listing.Status.SOLD
                || (viewer != null && (viewer.admin() || l.getSellerId().equals(viewer.id())));
        if (!visible) {
            throw BusinessException.of(ErrorCode.LISTING_NOT_FOUND);
        }
        return view(l, users.findById(l.getSellerId()).orElseThrow(), viewer);
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingView> mine(AuthUser seller, Listing.Status status, Integer page, Integer size) {
        return views(listings.mine(seller.id(), status, PageResponse.pageable(page, size)), seller);
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingView> adminList(AuthUser admin, Listing.Status status, Integer page, Integer size) {
        return views(listings.adminSearch(status, PageResponse.pageable(page, size)), admin);
    }

    @Transactional(readOnly = true)
    public SellerSummary seller(AuthUser seller) {
        List<LedgerEntry> fees = ledgerRepository.findByPayerUserIdAndTypeOrderByIdDesc(seller.id(), LedgerEntry.Type.SELLER_FEE);
        BigDecimal due = fees.stream().filter(LedgerEntry::isOpen).map(LedgerEntry::getAmount).reduce(Money.ZERO, BigDecimal::add);
        return new SellerSummary(listings.countBySellerIdAndStatusIn(seller.id(), QUOTA), config.seller().value().maxActiveListings(),
                due, fees.stream().map(LedgerService.View::of).toList());
    }

    @Transactional
    public ListingView create(AuthUser seller, ListingInput input) {
        requireIndividual(seller);
        Listing l = new Listing();
        l.setSellerId(seller.id());
        apply(l, input);
        listings.save(l);
        files.attach(seller.id(), input.photoKeys(), FilePurpose.LISTING_PHOTO, "LISTING", l.getId());
        return detail(seller, l.getId());
    }

    @Transactional
    public ListingView update(AuthUser seller, Long id, ListingInput input) {
        Listing l = own(seller, id);
        requireStatus(l, Listing.Status.DRAFT, Listing.Status.REJECTED);
        apply(l, input);
        files.attach(seller.id(), input.photoKeys(), FilePurpose.LISTING_PHOTO, "LISTING", l.getId());
        return detail(seller, id);
    }

    @Transactional
    public ListingView submit(AuthUser seller, Long id) {
        users.lockById(seller.id());
        Listing l = own(seller, id);
        requireStatus(l, Listing.Status.DRAFT, Listing.Status.REJECTED);
        var versioned = config.seller();
        if (listings.countBySellerIdAndStatusIn(seller.id(), QUOTA) >= versioned.value().maxActiveListings()) {
            throw BusinessException.of(ErrorCode.LISTING_LIMIT_REACHED,
                    "You can have at most " + versioned.value().maxActiveListings() + " listings in review or active.");
        }
        l.setRejectionReason(null);
        if (versioned.value().moderationRequired()) {
            l.setStatus(Listing.Status.PENDING_REVIEW);
            audit.record("LISTING_SUBMITTED", "LISTING", id, null);
        } else {
            publish(l);
        }
        return detail(seller, id);
    }

    @Transactional
    public ListingView markSold(AuthUser seller, Long id) {
        Listing l = own(seller, id);
        requireStatus(l, Listing.Status.ACTIVE);
        l.setStatus(Listing.Status.SOLD);
        l.setSoldAt(Instant.now(clock));
        chargeFee(l, Configs.FeeTiming.ON_SOLD);
        notifications.notify(seller.id(), NotificationType.LISTING_SOLD, "Listing sold", "\"" + l.getTitle() + "\" is marked as sold.",
                "LISTING", id);
        audit.record("LISTING_SOLD", "LISTING", id, null);
        return detail(seller, id);
    }

    @Transactional
    public ListingView close(AuthUser seller, Long id) {
        Listing l = own(seller, id);
        requireStatus(l, Listing.Status.DRAFT, Listing.Status.PENDING_REVIEW, Listing.Status.ACTIVE);
        l.setStatus(Listing.Status.CLOSED);
        l.setClosedAt(Instant.now(clock));
        return detail(seller, id);
    }

    @Transactional
    public ListingView approve(AuthUser admin, Long id) {
        Listing l = listings.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.LISTING_NOT_FOUND));
        requireStatus(l, Listing.Status.PENDING_REVIEW);
        publish(l);
        return detail(admin, id);
    }

    @Transactional
    public ListingView reject(AuthUser admin, Long id, String reason) {
        Listing l = listings.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.LISTING_NOT_FOUND));
        requireStatus(l, Listing.Status.PENDING_REVIEW);
        l.setStatus(Listing.Status.REJECTED);
        l.setRejectionReason(reason);
        notifications.notify(l.getSellerId(), NotificationType.LISTING_REJECTED, "Listing not approved",
                "\"" + l.getTitle() + "\": " + reason, "LISTING", id);
        audit.record("LISTING_REJECTED", "LISTING", id, reason);
        return detail(admin, id);
    }

    @Scheduled(fixedDelayString = "${wadii.jobs.expiration-interval-ms:60000}", initialDelay = 20_000)
    @Transactional
    public int expireListings() {
        List<Long> ids = listings.expired(Instant.now(clock));
        for (Listing l : listings.findAllById(ids)) {
            l.setStatus(Listing.Status.EXPIRED);
            notifications.notify(l.getSellerId(), NotificationType.LISTING_EXPIRED, "Listing expired",
                    "\"" + l.getTitle() + "\" reached the end of its listing period.", "LISTING", l.getId());
        }
        return ids.size();
    }

    private void publish(Listing l) {
        Instant now = Instant.now(clock);
        l.setStatus(Listing.Status.ACTIVE);
        l.setPublishedAt(now);
        l.setExpiresAt(now.plus(Duration.ofDays(config.seller().value().listingDurationDays())));
        chargeFee(l, Configs.FeeTiming.ON_PUBLISH);
        if (listings.findBySellerIdAndStatusAndTitleIgnoreCase(l.getSellerId(), Listing.Status.ACTIVE, l.getTitle()).size() > 1) {
            fraud.duplicateListing(l.getSellerId(), l.getTitle());
        }
        notifications.notify(l.getSellerId(), NotificationType.LISTING_PUBLISHED, "Listing published",
                "\"" + l.getTitle() + "\" is now visible to buyers.", "LISTING", l.getId());
        audit.record("LISTING_PUBLISHED", "LISTING", l.getId(), null);
    }

    private void chargeFee(Listing l, Configs.FeeTiming moment) {
        var versioned = config.seller();
        SellerConfig cfg = versioned.value();
        if (cfg.feeTiming() != moment || cfg.feeMode() == Configs.FeeMode.NONE) {
            return;
        }
        BigDecimal amount = cfg.feeMode() == Configs.FeeMode.FIXED ? Money.of(cfg.feeValue()) : Money.percentOf(l.getPrice(), cfg.feeValue());
        String rule = (cfg.feeMode() == Configs.FeeMode.FIXED ? "Fixed seller fee EGP " + cfg.feeValue()
                : cfg.feeValue() + "% of EGP " + l.getPrice()) + (moment == Configs.FeeTiming.ON_PUBLISH ? " on publication" : " on sale");
        ledger.due(new LedgerService.Charge(LedgerEntry.Type.SELLER_FEE, amount, l.getSellerId(), null, null, null, l.getId(),
                rule, versioned.versionId(), Times.today(clock).plusDays(30)));
    }

    private void apply(Listing l, ListingInput input) {
        catalog.requireArea(input.areaId());
        l.setTitle(input.title().trim());
        l.setCategory(input.category());
        l.setCondition(input.condition());
        l.setDescription(input.description().trim());
        l.setPrice(Money.of(input.price()));
        l.setAreaId(input.areaId());
        l.setLocationText(input.locationText());
        l.setContactMethod(input.contactMethod());
        l.setPhotoKeys(KeyList.join(input.photoKeys()));
    }

    private Listing own(AuthUser seller, Long id) {
        Listing l = listings.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.LISTING_NOT_FOUND));
        if (!l.getSellerId().equals(seller.id())) {
            throw BusinessException.of(ErrorCode.LISTING_NOT_FOUND);
        }
        return l;
    }

    private static void requireStatus(Listing l, Listing.Status... allowed) {
        if (!Set.of(allowed).contains(l.getStatus())) {
            throw BusinessException.of(ErrorCode.LISTING_INVALID_STATE);
        }
    }

    private static void requireIndividual(AuthUser user) {
        if (user.admin()) {
            throw BusinessException.forbidden("Staff accounts cannot sell items.");
        }
    }

    private PageResponse<ListingView> views(org.springframework.data.domain.Page<Listing> page, AuthUser viewer) {
        Map<Long, User> sellers = users.findAllById(page.getContent().stream().map(Listing::getSellerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(page, l -> view(l, sellers.get(l.getSellerId()), viewer));
    }

    private static ListingView view(Listing l, User seller, AuthUser viewer) {
        boolean mine = viewer != null && l.getSellerId().equals(viewer.id());
        String phone = l.getContactMethod() == Listing.ContactMethod.PHONE && l.getStatus() == Listing.Status.ACTIVE ? seller.getPhone() : null;
        return new ListingView(l.getId(), l.getSellerId(), seller.getFullName(), l.getTitle(), l.getCategory(), l.getCondition(),
                l.getDescription(), l.getPrice(), l.getAreaId(), l.getLocationText(), l.getContactMethod(), phone,
                KeyList.split(l.getPhotoKeys()), l.getStatus(), l.getRejectionReason(), l.getPublishedAt(), l.getExpiresAt(),
                l.getCreatedAt(), mine);
    }
}
