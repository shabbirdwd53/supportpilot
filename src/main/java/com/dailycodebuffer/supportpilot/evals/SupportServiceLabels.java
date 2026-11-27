package com.dailycodebuffer.supportpilot.evals;

import org.springframework.core.env.Environment;

final class SupportServiceLabels {

    private SupportServiceLabels() {
    }

    static String model(Environment env) {
        String provider = env.getProperty("spring.ai.model.chat", "ollama");
        return provider + "/" + env.getProperty("spring.ai." + provider + ".chat.model", "default");
    }
}
