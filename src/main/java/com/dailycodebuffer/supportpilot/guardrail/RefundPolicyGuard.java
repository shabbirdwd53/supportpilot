package com.dailycodebuffer.supportpilot.guardrail;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dailycodebuffer.supportpilot.ticket.Ticket;
import com.dailycodebuffer.supportpilot.ticket.TicketTriage;

/**
 * The business rule the model must never break, enforced in code:
 * refunds only within 30 days of delivery, never more than the order total,
 * and the reply can't promise an amount the decision doesn't allow.
 */
public class RefundPolicyGuard implements OutputGuardrail {

    public static final int REFUND_WINDOW_DAYS = 30;
    private static final Pattern REFUND_WORDS = Pattern.compile("(?i)\\b(refund\\w*|credit\\w*|reimburs\\w*|money back|compensat\\w*)");
    private static final Pattern MONEY = Pattern.compile("\\$\\s?(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)");

    @Override
    public String name() {
        return "refund_policy";
    }

    @Override
    public GuardrailResult check(Ticket ticket, TicketTriage triage) {
        var refund = triage.refund();
        var order = ticket.order();
        BigDecimal amount = refund.amount() == null ? BigDecimal.ZERO : refund.amount();

        if (refund.eligible()) {
            if (order == null) {
                return violation("refund approved but the ticket has no order");
            }
            if (order.daysSinceDelivery() == null) {
                return violation("refund approved for an order that hasn't been delivered yet");
            }
            if (order.daysSinceDelivery() > REFUND_WINDOW_DAYS) {
                return violation("refund approved " + order.daysSinceDelivery() + " days after delivery (window is "
                        + REFUND_WINDOW_DAYS + ")");
            }
            if (amount.signum() <= 0) {
                return violation("refund approved with amount " + amount);
            }
        } else if (amount.signum() > 0) {
            return violation("not eligible but amount is " + amount);
        }
        if (order != null && order.total() != null && amount.compareTo(order.total()) > 0) {
            return violation("refund " + amount + " exceeds order total " + order.total());
        }

        // The reply is what the customer reads, so it must not promise more money than the decision allows
        BigDecimal cap = refund.eligible() ? amount : BigDecimal.ZERO;
        for (String sentence : triage.reply().split("(?<=[.!?])\\s+")) {
            if (!REFUND_WORDS.matcher(sentence).find()) {
                continue; // "I can see your $40 order" is fine; "we'll refund $500" is not
            }
            Matcher m = MONEY.matcher(sentence);
            while (m.find()) {
                BigDecimal promised = new BigDecimal(m.group(1).replace(",", ""));
                if (promised.compareTo(cap) > 0) {
                    return violation("reply mentions $" + promised + " next to a refund, but the approved refund is $" + cap
                            + ". Do not repeat any other amount, even to decline it");
                }
            }
        }
        return GuardrailResult.pass(name());
    }

    private GuardrailResult violation(String detail) {
        return new GuardrailResult(name(), Verdict.VIOLATION, detail);
    }
}
