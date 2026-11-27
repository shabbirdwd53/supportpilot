package com.dailycodebuffer.supportpilot.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.dailycodebuffer.supportpilot.ticket.Category;
import com.dailycodebuffer.supportpilot.ticket.CustomerTier;
import com.dailycodebuffer.supportpilot.ticket.Order;
import com.dailycodebuffer.supportpilot.ticket.Priority;
import com.dailycodebuffer.supportpilot.ticket.RefundDecision;
import com.dailycodebuffer.supportpilot.ticket.Ticket;
import com.dailycodebuffer.supportpilot.ticket.TicketTriage;
import org.junit.jupiter.api.Test;

class OutputGuardrailsTest {

    private static final RefundPolicyGuard REFUND = new RefundPolicyGuard();

    private static Ticket order(String total, Integer days) {
        return new Ticket(CustomerTier.STANDARD, "msg", new Order("ORD-1", new BigDecimal(total), days));
    }

    private static TicketTriage triage(boolean eligible, String amount, String reply) {
        return new TicketTriage(Category.REFUND, Priority.MEDIUM, false,
                new RefundDecision(eligible, new BigDecimal(amount), "R1"), reply);
    }

    @Test
    void allowsRefundInsideWindowUpToTotal() {
        assertThat(REFUND.check(order("89.00", 4), triage(true, "89.00", "We'll refund $89.00 within 5-7 days.")).failed()).isFalse();
    }

    @Test
    void blocksRefundAboveOrderTotal() {
        var r = REFUND.check(order("40.00", 8), triage(true, "500", "Refund of $500 approved."));
        assertThat(r.failed()).isTrue();
        assertThat(r.detail()).contains("exceeds order total");
    }

    @Test
    void blocksRefundOutsideWindowAndUndelivered() {
        assertThat(REFUND.check(order("59.00", 31), triage(true, "59.00", "Approved.")).detail()).contains("31 days");
        assertThat(REFUND.check(order("59.00", null), triage(true, "59.00", "Approved.")).detail()).contains("delivered");
    }

    @Test
    void replyCannotPromiseMoreThanTheDecision() {
        var r = REFUND.check(order("40.00", 8), triage(true, "40.00", "Great news! Your $500 refund is on its way."));
        assertThat(r.failed()).isTrue();
        // Mentioning a price outside a refund sentence is fine
        assertThat(REFUND.check(order("40.00", 45), triage(false, "0", "I can see your $40.00 order from August.")).failed()).isFalse();
    }

    @Test
    void catchesCanaryAndPiiLeaks() {
        var guard = new DataLeakGuard("SP-CANARY-7f3a91");
        assertThat(guard.check(order("1", 1), triage(false, "0", "Your code is SP-CANARY-7f3a91")).detail()).contains("canary");
        assertThat(guard.check(order("1", 1), triage(false, "0", "Card 4111 1111 1111 1111 noted")).detail()).contains("CARD");
        assertThat(guard.check(order("1", 1), triage(false, "0", "Thanks, we're on it.")).failed()).isFalse();
    }

    @Test
    void securityTicketsMustEscalateUrgently() {
        var guard = new EscalationPolicyGuard();
        var notEscalated = new TicketTriage(Category.SECURITY, Priority.HIGH, false, RefundDecision.none("n/a"), "ok");
        assertThat(guard.check(order("1", 1), notEscalated).failed()).isTrue();
    }

    @Test
    void brandSafetyDenylist() {
        var guard = new BrandSafetyGuard(List.of("guarantee"));
        assertThat(guard.check(order("1", 1), triage(false, "0", "We GUARANTEE delivery tomorrow")).failed()).isTrue();
    }
}
