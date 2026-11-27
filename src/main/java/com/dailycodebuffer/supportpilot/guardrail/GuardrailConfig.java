package com.dailycodebuffer.supportpilot.guardrail;

import java.util.List;

import com.dailycodebuffer.supportpilot.SupportPilotProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The guardrail chain, in order. Add a rule = add a class + one line here + a unit test. */
@Configuration
public class GuardrailConfig {

    @Bean
    public List<InputGuardrail> inputGuardrails(SupportPilotProperties props) {
        return List.of(
                new InputLengthGuard(props.guardrails().maxInputChars()),
                new PromptInjectionGuard(),
                new PiiRedactionGuard());
    }

    @Bean
    public List<OutputGuardrail> outputGuardrails(SupportPilotProperties props) {
        return List.of(
                new RefundPolicyGuard(),
                new EscalationPolicyGuard(),
                new DataLeakGuard(props.guardrails().canary()),
                new BrandSafetyGuard(props.guardrails().bannedPhrases()));
    }
}
