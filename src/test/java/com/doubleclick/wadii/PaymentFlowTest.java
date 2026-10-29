package com.doubleclick.wadii;

import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi;
import com.doubleclick.wadii.support.TestApi.Actor;
import com.doubleclick.wadii.support.TestApi.Result;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.doubleclick.wadii.support.TestApi.expectOk;
import static com.doubleclick.wadii.support.TestApi.expectStatus;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentFlowTest extends IntegrationTest {

    @Autowired
    TestApi api;
    @Autowired
    JdbcTemplate jdbc;

    private Result uploadProof(Actor actor, long paymentId, String ref) {
        return api.multipart("/api/v1/payments/" + paymentId + "/proofs", actor, "proof.png", "image/png", TestApi.PNG,
                ref == null ? Map.of() : Map.of("transactionReference", ref));
    }

    private Actor finance() {
        String email = TestApi.uniqueEmail("finance");
        expectStatus(201, api.post("/api/v1/admin/users/admins", api.admin(), Map.of("firstName", "Fin", "email", email,
                "password", "FinanceAdmin123!", "adminRoles", Set.of("FINANCE_ADMIN"))));
        Result login = expectOk(api.post("/api/v1/auth/login", null, Map.of("email", email, "password", "FinanceAdmin123!")));
        return new Actor(login.data().path("token").asText(), login.data().path("user").path("id").asLong(), null, email);
    }

    @Test
    void vodafoneCashProofIsNotAConfirmationAndOnlyTheAcceptedProviderConfirms() {
        long s = api.newService();
        Actor provider = api.verifiedProvider(List.of(s));
        Actor otherProvider = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long[] ids = api.acceptedOrder(customer, provider, List.of(s), new BigDecimal("400.00"));
        long orderId = ids[0], paymentId = ids[2];

        JsonNode selected = expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "VODAFONE_CASH"))).data();
        assertThat(selected.path("payee").path("accountIdentifier").asText()).isEqualTo("01011111111");
        assertThat(selected.path("amount").decimalValue()).isEqualByComparingTo("400.00");

        JsonNode submitted = expectOk(uploadProof(customer, paymentId, "VC-" + paymentId)).data();
        assertThat(submitted.path("status").asText()).isEqualTo("PROOF_SUBMITTED");
        assertThat(submitted.path("amountPaid").decimalValue()).isEqualByComparingTo("0");

        Result start = api.post("/api/v1/orders/" + orderId + "/start", provider, null);
        assertThat(start.code()).isEqualTo("ORDER_NOT_FULLY_PAID");

        assertThat(api.post("/api/v1/payments/" + paymentId + "/confirm", otherProvider, null).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/payments/" + paymentId + "/confirm", customer, null).status()).isEqualTo(403);

        JsonNode confirmed = expectOk(api.post("/api/v1/payments/" + paymentId + "/confirm", provider, null)).data();
        assertThat(confirmed.path("status").asText()).isEqualTo("PROVIDER_CONFIRMED");
        assertThat(confirmed.path("amountPaid").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(confirmed.path("proofs").get(0).path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("ACCEPTED");

        expectOk(api.post("/api/v1/orders/" + orderId + "/start", provider, null));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'PAYMENT' AND entity_id = ? AND action IN "
                + "('PAYMENT_CREATED','PAYMENT_METHOD_SELECTED','PAYMENT_PROOF_UPLOADED','PAYMENT_CONFIRMED')", Integer.class, paymentId)).isEqualTo(4);
    }

    @Test
    void paymentProofsArePrivate() {
        long s = api.newService();
        Actor provider = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long paymentId = api.acceptedOrder(customer, provider, List.of(s), new BigDecimal("100"))[2];
        expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "INSTAPAY")));
        long proofId = expectOk(uploadProof(customer, paymentId, null)).data().path("proofs").get(0).path("id").asLong();

        assertThat(api.rawStatus(MockMvcRequestBuilders.get("/api/v1/payment-proofs/" + proofId + "/file"), null)).isEqualTo(401);
        assertThat(api.rawStatus(MockMvcRequestBuilders.get("/api/v1/payment-proofs/" + proofId + "/file"), api.customer())).isEqualTo(403);
        assertThat(api.rawStatus(MockMvcRequestBuilders.get("/api/v1/payment-proofs/" + proofId + "/file"), customer)).isEqualTo(200);
        assertThat(api.rawStatus(MockMvcRequestBuilders.get("/api/v1/payment-proofs/" + proofId + "/file"), provider)).isEqualTo(200);
        String key = jdbc.queryForObject("SELECT file_key FROM payment_proofs WHERE id = ?", String.class, proofId);
        assertThat(api.rawStatus(MockMvcRequestBuilders.get("/api/v1/files/public/" + key), null)).isEqualTo(404);
        assertThat(api.get("/api/v1/payments/" + paymentId, api.customer()).status()).isEqualTo(403);
    }

    @Test
    void instapayRejectionThenNewPaymentPreservesHistory() {
        long s = api.newService();
        Actor provider = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long[] ids = api.acceptedOrder(customer, provider, List.of(s), new BigDecimal("250"));
        long paymentId = ids[2];
        expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "INSTAPAY")));
        expectOk(uploadProof(customer, paymentId, "IP-1-" + paymentId));
        JsonNode rejected = expectOk(api.post("/api/v1/payments/" + paymentId + "/reject", provider, Map.of("reason", "Nothing arrived"))).data();
        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.path("proofs").get(0).path("status").asText()).isEqualTo("REJECTED");

        JsonNode second = expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "INSTAPAY"))).data();
        assertThat(second.path("id").asLong()).isNotEqualTo(paymentId);
        assertThat(second.path("status").asText()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id = ?", Integer.class, ids[0])).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_proofs WHERE payment_id = ?", Integer.class, paymentId)).isEqualTo(1);
    }

    @Test
    void changingMethodAfterAProofCancelsAndReopens() {
        long s = api.newService();
        Actor provider = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long paymentId = api.acceptedOrder(customer, provider, List.of(s), new BigDecimal("150"))[2];
        expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "VODAFONE_CASH")));
        expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "INSTAPAY")));
        assertThat(jdbc.queryForObject("SELECT method FROM payments WHERE id = ?", String.class, paymentId)).isEqualTo("INSTAPAY");
        expectOk(uploadProof(customer, paymentId, null));
        JsonNode changed = expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "CASH"))).data();
        assertThat(changed.path("id").asLong()).isNotEqualTo(paymentId);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId)).isEqualTo("CANCELED");
        assertThat(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "CARD")).code())
                .isIn("PAYMENT_METHOD_NOT_SUPPORTED", "PAYMENT_INVALID_STATE");
    }

    @Test
    void cashIsConfirmedByTheProviderAndTheFullFlowCompletes() {
        long s = api.newService();
        Actor provider = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long[] ids = api.acceptedOrder(customer, provider, List.of(s), new BigDecimal("300"));
        long orderId = ids[0], paymentId = ids[2];
        expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "CASH")));
        assertThat(uploadProof(customer, paymentId, null).code()).isEqualTo("PAYMENT_PROOF_NOT_ALLOWED");
        expectOk(api.post("/api/v1/payments/" + paymentId + "/confirm", provider, null));
        expectOk(api.post("/api/v1/orders/" + orderId + "/start", provider, null));

        // additional charge: requested by provider, approved by customer -> new payment for the difference
        long chargeId = expectStatus(201, api.post("/api/v1/orders/" + orderId + "/charges", provider,
                Map.of("amount", new BigDecimal("80.00"), "reason", "Extra belt"))).data().path("id").asLong();
        assertThat(api.post("/api/v1/charges/" + chargeId + "/decision", provider, Map.of("approve", true)).status()).isEqualTo(403);
        expectOk(api.post("/api/v1/charges/" + chargeId + "/decision", customer, Map.of("approve", true)));
        JsonNode fin = expectOk(api.get("/api/v1/orders/" + orderId + "/financials", customer)).data();
        assertThat(fin.path("amountDue").decimalValue()).isEqualByComparingTo("380.00");
        assertThat(fin.path("outstanding").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(jdbc.queryForObject("SELECT price FROM order_responses WHERE id = ?", BigDecimal.class, ids[1])).isEqualByComparingTo("300");

        expectOk(api.post("/api/v1/orders/" + orderId + "/mark-done", provider, null));
        assertThat(api.post("/api/v1/orders/" + orderId + "/confirm-completion", customer, null).code()).isEqualTo("ORDER_NOT_FULLY_PAID");
        long second = jdbc.queryForObject("SELECT id FROM payments WHERE order_id = ? AND status = 'PENDING'", Long.class, orderId);
        expectOk(api.post("/api/v1/payments/" + second + "/method", customer, Map.of("method", "CASH")));
        expectOk(api.post("/api/v1/payments/" + second + "/confirm", provider, null));
        expectOk(api.post("/api/v1/orders/" + orderId + "/confirm-completion", customer, null));

        JsonNode invoice = expectOk(api.get("/api/v1/orders/" + orderId + "/invoice", customer)).data();
        assertThat(invoice.path("total").decimalValue()).isEqualByComparingTo("380.00");
        assertThat(invoice.path("paid").decimalValue()).isEqualByComparingTo("380.00");
        assertThat(invoice.path("remaining").decimalValue()).isEqualByComparingTo("0");
        assertThat(invoice.path("providerNet").decimalValue()).isEqualByComparingTo("380.00");

        assertThat(api.post("/api/v1/orders/" + orderId + "/rating", api.customer(), Map.of("score", 5)).status()).isEqualTo(403);
        expectStatus(201, api.post("/api/v1/orders/" + orderId + "/rating", customer, Map.of("score", 4, "comment", "Good")));
        assertThat(api.post("/api/v1/orders/" + orderId + "/rating", customer, Map.of("score", 5)).code()).isEqualTo("RATING_ALREADY_EXISTS");
        assertThat(jdbc.queryForObject("SELECT rating_count FROM providers WHERE id = ?", Integer.class, provider.providerId())).isEqualTo(1);
    }

    @Test
    void paymentDisputeIsResolvedByFinance() {
        long s = api.newService();
        Actor provider = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long[] ids = api.acceptedOrder(customer, provider, List.of(s), new BigDecimal("500"));
        long orderId = ids[0], paymentId = ids[2];
        expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "VODAFONE_CASH")));
        expectOk(uploadProof(customer, paymentId, "D-" + paymentId));
        expectOk(api.post("/api/v1/payments/" + paymentId + "/reject", provider, Map.of("reason", "Not received")));

        JsonNode dispute = expectStatus(201, api.post("/api/v1/orders/" + orderId + "/disputes", customer,
                Map.of("type", "PAYMENT_DISPUTE", "reason", "I transferred the money", "paymentId", paymentId))).data();
        long disputeId = dispute.path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId)).isEqualTo("DISPUTED");
        assertThat(api.get("/api/v1/disputes/" + disputeId, api.customer()).status()).isEqualTo(403);
        expectStatus(201, api.multipart("/api/v1/disputes/" + disputeId + "/evidence", customer, "receipt.png", "image/png", TestApi.PNG,
                Map.of("kind", "PAYMENT_PROOF", "note", "bank receipt")));

        Actor support = api.admin();
        Actor finance = finance();
        Result resolvedBySupportOnly = api.post("/api/v1/admin/disputes/" + disputeId + "/resolve",
                new Actor(api.customer().token(), 0, null, null), Map.of("outcome", "PAYMENT_CONFIRMED", "resolution", "x"));
        assertThat(resolvedBySupportOnly.status()).isEqualTo(403);
        expectOk(api.post("/api/v1/admin/disputes/" + disputeId + "/status", finance, Map.of("status", "UNDER_REVIEW")));
        expectOk(api.post("/api/v1/admin/disputes/" + disputeId + "/resolve", finance,
                Map.of("outcome", "PAYMENT_CONFIRMED", "resolution", "Bank statement shows the transfer")));
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId)).isEqualTo("PROVIDER_CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT status FROM disputes WHERE id = ?", String.class, disputeId)).isEqualTo("RESOLVED");
        assertThat(support).isNotNull();
    }

    @Test
    void partialRefundsAreBoundedAndProcessedByTheProvider() {
        long s = api.newService();
        Actor provider = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long[] ids = api.acceptedOrder(customer, provider, List.of(s), new BigDecimal("200"));
        long orderId = ids[0], paymentId = ids[2];
        assertThat(api.post("/api/v1/orders/" + orderId + "/refunds", customer, Map.of("amount", 50, "reason", "x")).code())
                .isEqualTo("REFUND_INVALID_STATE");
        expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "CASH")));
        expectOk(api.post("/api/v1/payments/" + paymentId + "/confirm", provider, null));

        Result tooMuch = api.post("/api/v1/orders/" + orderId + "/refunds", customer, Map.of("amount", new BigDecimal("250"), "reason", "x"));
        assertThat(tooMuch.status()).isEqualTo(422);
        assertThat(tooMuch.code()).isEqualTo("REFUND_AMOUNT_INVALID");
        long refundId = expectStatus(201, api.post("/api/v1/orders/" + orderId + "/refunds", customer,
                Map.of("amount", new BigDecimal("60.00"), "reason", "Part not installed"))).data().path("id").asLong();
        assertThat(api.post("/api/v1/refunds/" + refundId + "/decision", customer, Map.of("approve", true)).status()).isEqualTo(403);
        expectOk(api.post("/api/v1/refunds/" + refundId + "/decision", provider, Map.of("approve", true)));
        expectOk(api.post("/api/v1/refunds/" + refundId + "/complete", provider, null));
        Map<String, Object> payment = jdbc.queryForMap("SELECT status, amount_refunded FROM payments WHERE id = ?", paymentId);
        assertThat(payment.get("status")).isEqualTo("PROVIDER_CONFIRMED");
        assertThat((BigDecimal) payment.get("amount_refunded")).isEqualByComparingTo("60.00");
        assertThat(api.post("/api/v1/orders/" + orderId + "/refunds", customer, Map.of("amount", new BigDecimal("140.01"), "reason", "x")).code())
                .isEqualTo("REFUND_AMOUNT_INVALID");
    }

    @Test
    void providerCancellationRefundsEverythingPaid() {
        long s = api.newService();
        Actor provider = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long[] ids = api.acceptedOrder(customer, provider, List.of(s), new BigDecimal("120"));
        expectOk(api.post("/api/v1/payments/" + ids[2] + "/method", customer, Map.of("method", "CASH")));
        expectOk(api.post("/api/v1/payments/" + ids[2] + "/confirm", provider, null));
        assertThat(api.post("/api/v1/orders/" + ids[0] + "/cancel", provider, Map.of()).code()).isEqualTo("VALIDATION_FAILED");
        Result canceled = expectOk(api.post("/api/v1/orders/" + ids[0] + "/cancel", provider, Map.of("reason", "Parts unavailable")));
        assertThat(canceled.data().path("refundIds").size()).isEqualTo(1);
        Map<String, Object> refund = jdbc.queryForMap("SELECT amount, status, source FROM refunds WHERE order_id = ?", ids[0]);
        assertThat((BigDecimal) refund.get("amount")).isEqualByComparingTo("120");
        assertThat(refund.get("status")).isEqualTo("APPROVED");
        assertThat(refund.get("source")).isEqualTo("CANCELLATION");
    }
}
