package com.dailycodebuffer.supportpilot.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** The dataset is code too: a broken or duplicated case silently skews every score. */
class DatasetLintTest {

    private static final List<Dataset> DATASETS = Stream.of("evals/datasets/golden.json", "evals/datasets/redteam.json")
            .map(Path::of).map(Dataset::load).toList();

    @Test
    void everyDatasetHasNameAndVersion() {
        DATASETS.forEach(d -> {
            assertThat(d.name()).isNotBlank();
            assertThat(d.version()).as(d.name() + " version").matches("\\d{4}\\.\\d{1,2}\\.\\d+");
        });
    }

    @Test
    void caseIdsAreUniqueAcrossDatasets() {
        var ids = new HashSet<String>();
        DATASETS.stream().flatMap(d -> d.cases().stream()).forEach(c ->
                assertThat(ids.add(c.id())).as("duplicate case id " + c.id()).isTrue());
    }

    @Test
    void everyCaseHasATicketAndExpectations() {
        DATASETS.stream().flatMap(d -> d.cases().stream()).forEach(c -> {
            assertThat(c.ticket()).as(c.id()).isNotNull();
            assertThat(c.ticket().message()).as(c.id()).isNotBlank();
            assertThat(c.expected()).as(c.id()).isNotNull();
        });
    }

    @Test
    void goldenCasesHaveReferenceRepliesForTheJudge() {
        DATASETS.stream().filter(d -> d.name().equals("golden")).flatMap(d -> d.cases().stream())
                .forEach(c -> assertThat(c.referenceReply()).as(c.id()).isNotBlank());
    }

    @Test
    void refundExpectationsRespectThePolicy() {
        // A label that contradicts the policy would teach the model the wrong thing
        DATASETS.stream().flatMap(d -> d.cases().stream())
                .filter(c -> Boolean.TRUE.equals(c.expected().refundEligible()))
                .forEach(c -> {
                    var order = c.ticket().order();
                    assertThat(order).as(c.id()).isNotNull();
                    assertThat(order.daysSinceDelivery()).as(c.id()).isNotNull().isLessThanOrEqualTo(30);
                    if (c.expected().refundAmount() != null) {
                        assertThat(c.expected().refundAmount()).as(c.id()).isLessThanOrEqualTo(order.total());
                    }
                });
    }
}
