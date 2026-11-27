package com.dailycodebuffer.supportpilot.observability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dailycodebuffer.supportpilot.SupportPilotProperties;
import com.dailycodebuffer.supportpilot.ticket.SupportResponse;
import com.dailycodebuffer.supportpilot.ticket.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every production request becomes a trace: input (redacted), output, guardrail verdicts,
 * prompt version, model, latency, tokens. A JSONL file keeps the demo dependency-free;
 * in production send the same record to Langfuse, Arize Phoenix, OpenTelemetry or your data lake.
 */
@Component
public class TraceStore {

    private static final Logger log = LoggerFactory.getLogger(TraceStore.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final boolean enabled;
    private final Path file;

    public TraceStore(SupportPilotProperties props) {
        this.enabled = props.observability().tracesEnabled();
        this.file = props.observability().dataDir().resolve("traces.jsonl");
    }

    public synchronized void record(Ticket ticket, SupportResponse response) {
        if (!enabled) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.writeValueAsString(new TraceRecord(Instant.now(), ticket, response)) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not write trace {}: {}", response.traceId(), e.getMessage());
        }
    }

    public List<TraceRecord> all() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.filter(l -> !l.isBlank()).map(l -> JSON.readValue(l, TraceRecord.class)).toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public Optional<TraceRecord> find(String traceId) {
        return all().stream().filter(t -> t.response().traceId().equals(traceId)).findFirst();
    }
}
