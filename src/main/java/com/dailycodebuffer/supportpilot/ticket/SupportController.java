package com.dailycodebuffer.supportpilot.ticket;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class SupportController {

    private final SupportService support;

    public SupportController(SupportService support) {
        this.support = support;
    }

    /** curl -s localhost:8080/api/tickets -H 'Content-Type: application/json' -d @requests/refund.json */
    @PostMapping("/triage")
    public SupportResponse triage(@Valid @RequestBody Ticket ticket) {
        return support.handle(ticket);
    }
}
