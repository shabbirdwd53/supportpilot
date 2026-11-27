package com.dailycodebuffer.supportpilot.observability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.dailycodebuffer.supportpilot.SupportPilotProperties;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Closes the loop: agents rate replies, bad ones are promoted into the eval dataset. */
@RestController
public class FeedbackController {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TraceStore traces;
    private final Path file;

    public FeedbackController(TraceStore traces, SupportPilotProperties props) {
        this.traces = traces;
        this.file = props.observability().dataDir().resolve("feedback.jsonl");
    }

    @PostMapping("/api/tickets/{traceId}/feedback")
    public ResponseEntity<Map<String, String>> feedback(@PathVariable String traceId, @Valid @RequestBody Feedback body)
            throws IOException {
        if (traces.find(traceId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var record = new Feedback(traceId, body.rating(), body.comment(), Instant.now());
        synchronized (this) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.writeValueAsString(record) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        return ResponseEntity.ok(Map.of("status", "recorded", "traceId", traceId));
    }

    public List<Feedback> all() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.filter(l -> !l.isBlank()).map(l -> JSON.readValue(l, Feedback.class)).toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
