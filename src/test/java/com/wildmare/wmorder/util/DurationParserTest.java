package com.wildmare.wmorder.util;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class DurationParserTest {
    @Test void parsesCompactAndIsoDurations() {
        assertThat(DurationParser.parse("7d")).isEqualTo(Duration.ofDays(7));
        assertThat(DurationParser.parse("2w")).isEqualTo(Duration.ofDays(14));
        assertThat(DurationParser.parse("PT90M")).isEqualTo(Duration.ofMinutes(90));
    }

    @Test void rejectsMalformedDuration() {
        assertThatThrownBy(() -> DurationParser.parse("tomorrow")).isInstanceOf(IllegalArgumentException.class);
    }
}
