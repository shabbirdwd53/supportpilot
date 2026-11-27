package com.dailycodebuffer.supportpilot.guardrail;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

import com.dailycodebuffer.supportpilot.ticket.Ticket;

/**
 * First line of defence against prompt injection: known attack phrasings, after normalising
 * the text so zero-width characters and odd spacing can't hide them.
 * <p>
 * It will NOT catch everything (try another language). That's why output guardrails exist,
 * and why the red-team eval suite runs every attack through the full pipeline in CI.
 */
public class PromptInjectionGuard implements InputGuardrail {

    private static final List<Pattern> ATTACKS = List.of(
            p("\\b(ignore|disregard|forget|override)\\b.{0,40}\\b(previous|prior|above|earlier|all|your|the)\\b.{0,30}\\b(instructions?|rules|prompts?|polic(y|ies)|guidelines)\\b"),
            p("\\b(system|developer|hidden|initial)\\s+(prompt|message|instructions?)\\b"),
            p("\\b(reveal|print|show|repeat|output|leak)\\b.{0,30}\\b(instructions?|prompt|rules|configuration)\\b"),
            p("\\byou\\s+are\\s+now\\b"),
            p("\\b(developer|god|dan|jailbreak|unrestricted)\\s+mode\\b"),
            p("\\bno\\s+(limits|restrictions|rules)\\b"),
            p("<\\s*/?\\s*(system|assistant|instructions?)\\s*>"),
            p("\\bnew\\s+(instructions|policy|rules)\\s*:"),
            p("\\bpretend\\s+(to\\s+be|you\\s+are)\\b"));

    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200F\\u2060\\uFEFF\\u00AD]");

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    @Override
    public String name() {
        return "prompt_injection";
    }

    @Override
    public Outcome check(Ticket ticket) {
        String text = normalise(ticket.message());
        for (Pattern attack : ATTACKS) {
            var m = attack.matcher(text);
            if (m.find()) {
                return new Outcome(ticket, new GuardrailResult(name(), Verdict.BLOCKED,
                        "matched \"" + m.group().trim() + "\""));
            }
        }
        return Outcome.pass(name(), ticket);
    }

    static String normalise(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
        s = INVISIBLE.matcher(s).replaceAll("");
        return s.replaceAll("\\s+", " ");
    }
}
