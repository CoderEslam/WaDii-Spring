package com.doubleclick.wadii.seed;

import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.catalog.CatalogServiceRepository;
import com.doubleclick.wadii.catalog.City;
import com.doubleclick.wadii.catalog.CityRepository;
import com.doubleclick.wadii.identity.Role;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.payment.PaymentMethod;
import com.doubleclick.wadii.provider.BranchWorkingHours;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderBranch;
import com.doubleclick.wadii.provider.ProviderPaymentAccount;
import com.doubleclick.wadii.provider.ProviderPaymentAccountRepository;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.provider.ProviderStats;
import com.doubleclick.wadii.provider.ProviderStatsRepository;
import com.doubleclick.wadii.provider.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;

/**
 * Development data, only with the {@code dev} profile and only on an empty user table.
 * Reference data (countries, cities, services, ...) comes from Flyway V2 and exists in every environment.
 * The shared password comes from WADII_SEED_PASSWORD or is generated randomly and printed once; there are no
 * hard-coded credentials.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final ProviderStatsRepository statsRepository;
    private final ProviderPaymentAccountRepository accountRepository;
    private final CityRepository cityRepository;
    private final CatalogServiceRepository serviceRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${WADII_SEED_PASSWORD:}")
    private String seedPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 1) return; // only the bootstrap admin may exist
        String password = seedPassword == null || seedPassword.isBlank() ? randomPassword() : seedPassword;
        City city = cityRepository.findAll().stream().findFirst().orElse(null);
        List<CatalogService> services = serviceRepository.findByActiveTrueOrderByNameAsc();
        user("customer1@wadii.dev", "Mona", "Customer", Role.USER, password, city);
        user("customer2@wadii.dev", "Karim", "Customer", Role.USER, password, city);
        provider("provider1@wadii.dev", "Cairo Auto Care", password, city, services, new BigDecimal("30.044420"), new BigDecimal("31.235712"));
        provider("provider2@wadii.dev", "Giza Motors", password, city, services.subList(0, Math.min(2, services.size())),
                new BigDecimal("30.013056"), new BigDecimal("31.208853"));
        provider("provider3@wadii.dev", "Nasr City Garage", password, city, services, new BigDecimal("30.056000"), new BigDecimal("31.330000"));
        log.warn("DEV seed created customer1..2@wadii.dev and provider1..3@wadii.dev with password: {}", password);
    }

    private User user(String email, String first, String last, Role role, String password, City city) {
        User u = new User();
        u.setEmail(email);
        u.setFirstName(first);
        u.setLastName(last);
        u.setRole(role);
        u.setCity(city);
        u.setPhone("+2010000000" + (userRepository.count() % 10));
        u.setPasswordHash(passwordEncoder.encode(password));
        return userRepository.save(u);
    }

    private void provider(String email, String name, String password, City city, List<CatalogService> services,
                          BigDecimal lat, BigDecimal lng) {
        User u = user(email, name.split(" ")[0], "Owner", Role.PROVIDER, password, city);
        Provider p = new Provider();
        p.setUser(u);
        p.setName(name);
        p.setDescription("Seeded development provider");
        p.setVerificationStatus(VerificationStatus.VERIFIED);
        p.setVerifiedAt(Instant.now());
        p.getServices().addAll(services);
        ProviderBranch b = new ProviderBranch();
        b.setProvider(p);
        b.setName(name + " - Main");
        b.setAddress("Cairo");
        b.setLatitude(lat);
        b.setLongitude(lng);
        for (int day = 1; day <= 7; day++) {
            if (day == 5) continue; // closed on Friday
            BranchWorkingHours h = new BranchWorkingHours();
            h.setBranch(b);
            h.setDayOfWeek(day);
            h.setOpensAt(LocalTime.of(9, 0));
            h.setClosesAt(LocalTime.of(21, 0));
            b.getWorkingHours().add(h);
        }
        p.getBranches().add(b);
        p = providerRepository.save(p);
        statsRepository.save(new ProviderStats(p.getId()));
        for (PaymentMethod m : List.of(PaymentMethod.VODAFONE_CASH, PaymentMethod.INSTAPAY)) {
            ProviderPaymentAccount a = new ProviderPaymentAccount();
            a.setProvider(p);
            a.setMethod(m);
            a.setAccountName(name);
            a.setAccountIdentifier(m == PaymentMethod.VODAFONE_CASH ? "01000000000" : name.toLowerCase().replace(' ', '.') + "@instapay");
            accountRepository.save(a);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
