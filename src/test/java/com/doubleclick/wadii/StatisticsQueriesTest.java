package com.doubleclick.wadii;

import com.doubleclick.wadii.advertisement.AdvertisementService;
import com.doubleclick.wadii.provider.ReliabilityService;
import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi;
import com.doubleclick.wadii.support.TestApi.Actor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The hand-written statistics SQL must stay portable between PostgreSQL and MySQL / MariaDB. */
class StatisticsQueriesTest extends IntegrationTest {

    @Autowired
    TestApi api;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ReliabilityService reliabilityService;
    @Autowired
    AdvertisementService advertisementService;

    @Test
    void reliabilityRecomputeAveragesResponseTimeInMinutes() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long orderId = api.order(customer, List.of(s), null);
        Instant notified = Instant.parse("2026-01-01T10:00:00Z");
        jdbc.update("UPDATE order_provider_targets SET notified_at = ?, responded_at = ? WHERE order_id = ? AND provider_id = ?",
                Timestamp.from(notified), Timestamp.from(notified.plusSeconds(30 * 60 + 45)), orderId, p.providerId());

        reliabilityService.recompute();

        BigDecimal avg = jdbc.queryForObject("SELECT avg_response_minutes FROM provider_stats WHERE provider_id = ?",
                BigDecimal.class, p.providerId());
        assertThat(avg).isEqualByComparingTo("30.75");
        assertThat(jdbc.queryForObject("SELECT targeted_count FROM provider_stats WHERE provider_id = ?",
                Integer.class, p.providerId())).isEqualTo(1);
    }

    @Test
    void adTrackingCreatesThenIncrementsTheDailyRow() {
        jdbc.update("INSERT INTO advertisements (title, status, priority, impressions, clicks, created_at, updated_at) "
                + "VALUES ('Stats ad', 'ACTIVE', 0, 0, 0, ?, ?)", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        long adId = jdbc.queryForObject("SELECT MAX(id) FROM advertisements", Long.class);

        advertisementService.trackImpression(adId);
        advertisementService.trackImpression(adId);
        advertisementService.trackClick(adId);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT impressions, clicks FROM advertisement_daily_stats WHERE advertisement_id = ?", adId);
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("impressions")).longValue()).isEqualTo(2);
        assertThat(((Number) rows.get(0).get("clicks")).longValue()).isEqualTo(1);
    }
}
