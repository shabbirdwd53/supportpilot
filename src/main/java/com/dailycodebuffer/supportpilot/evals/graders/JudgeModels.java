package com.dailycodebuffer.supportpilot.evals.graders;

import com.dailycodebuffer.supportpilot.SupportPilotProperties;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * The judge can run on a different (usually stronger) model than the app under test.
 * Typical CI setup: app on a small fast model, judge on a bigger one.
 */
public final class JudgeModels {

    public static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-5-5";

    private JudgeModels() {
    }

    public static ChatClient.Builder builderFor(SupportPilotProperties.Judge judge, ChatClient.Builder appBuilder,
                                                String appProvider) {
        String provider = judge.provider() == null ? "" : judge.provider().trim().toLowerCase();
        return switch (provider) {
            case "" -> {
                var options = ChatOptions.builder();
                if (!"anthropic".equals(appProvider)) {
                    options.temperature(0.0); // repeatable grades; current Claude models reject temperature
                }
                if (judge.model() != null && !judge.model().isBlank()) {
                    options.model(judge.model());
                }
                yield appBuilder.clone().defaultOptions(options);
            }
            case "anthropic" -> ChatClient.builder(anthropic(judge.model()));
            default -> throw new IllegalArgumentException(
                    "Unsupported supportpilot.judge.provider '" + provider + "'. Use blank (same as the app) or 'anthropic'.");
        };
    }

    public static String label(SupportPilotProperties.Judge judge, String appLabel) {
        if (judge.provider().isBlank()) {
            return judge.model().isBlank() ? appLabel : appLabel.split("/")[0] + "/" + judge.model();
        }
        return judge.provider() + "/" + (judge.model().isBlank() ? DEFAULT_ANTHROPIC_MODEL : judge.model());
    }

    private static AnthropicChatModel anthropic(String model) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("JUDGE_PROVIDER=anthropic needs the ANTHROPIC_API_KEY environment variable");
        }
        var options = AnthropicChatOptions.builder()
                .apiKey(apiKey)
                .model(model == null || model.isBlank() ? DEFAULT_ANTHROPIC_MODEL : model)
                .maxTokens(1024)
                .build();
        return AnthropicChatModel.builder().options(options).build();
    }
}
