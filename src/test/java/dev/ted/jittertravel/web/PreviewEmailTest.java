package dev.ted.jittertravel.web;

import dev.ted.jittertravel.web.PreviewEmail.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewEmailTest {

    @Test
    void aLinkIsItsOwnSegmentAndTheTextAroundItIsKeptByteForByte() {
        PreviewEmail email = new PreviewEmail("Flight booked", "email/flight-booked.txt", "Subject",
                "See it here: https://jittertravel.com/calendar?day=2026-10-18\n\nUA2091");

        assertThat(email.segments())
                .containsExactly(
                        new Segment("See it here: ", false),
                        new Segment("https://jittertravel.com/calendar?day=2026-10-18", true),
                        new Segment("\n\nUA2091", false));
    }

    @Test
    void joiningTheSegmentsGivesBackTheBodyExactly() {
        String body = "Before https://a.example/x?y=1 middle http://b.example after\nlast line";

        PreviewEmail email = new PreviewEmail("L", "f", "S", body);

        assertThat(email.segments().stream().map(Segment::text).reduce("", String::concat))
                .isEqualTo(body);
    }

    @Test
    void aBodyWithNoLinkIsOneTextSegment() {
        assertThat(new PreviewEmail("L", "f", "S", "Just words.").segments())
                .isEqualTo(List.of(new Segment("Just words.", false)));
    }
}
