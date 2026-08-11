package dev.ted.jittertravel.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The instance labels itself {@code production} only when a hosting-environment marker is present
 * (Railway injects {@code RAILWAY_ENVIRONMENT_NAME}); an absent or blank marker means it is running
 * locally.
 */
class BackupSourceTest {

    @Test
    void aPresentEnvironmentMarkerMeansProduction() {
        assertThat(new BackupSource("production").label())
                .isEqualTo("production");
    }

    @Test
    void anAbsentMarkerMeansLocal() {
        assertThat(new BackupSource(null).label())
                .isEqualTo("local");
    }

    @Test
    void aBlankMarkerMeansLocal() {
        assertThat(new BackupSource("   ").label())
                .isEqualTo("local");
    }
}
