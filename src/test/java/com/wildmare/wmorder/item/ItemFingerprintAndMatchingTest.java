package com.wildmare.wmorder.item;

import com.wildmare.wmorder.util.Hashing;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ItemFingerprintAndMatchingTest {
    @Test void fingerprintIsDeterministicAndMetadataSensitive() {
        String a = Hashing.sha256("DIAMOND_SWORD|name=A|sharpness=5".getBytes(StandardCharsets.UTF_8));
        String b = Hashing.sha256("DIAMOND_SWORD|name=A|sharpness=5".getBytes(StandardCharsets.UTF_8));
        String manipulated = Hashing.sha256("DIAMOND_SWORD|name=A|sharpness=4".getBytes(StandardCharsets.UTF_8));
        assertThat(a).isEqualTo(b).hasSize(64);
        assertThat(manipulated).isNotEqualTo(a);
    }

    @Test void matchingModesUseTheCorrectSignal() {
        assertThat(MatchDecision.matches(MatchingMode.MATERIAL_ONLY, true, false, false)).isTrue();
        assertThat(MatchDecision.matches(MatchingMode.SIMILAR, true, true, false)).isTrue();
        assertThat(MatchDecision.matches(MatchingMode.SIMILAR, true, false, true)).isFalse();
        assertThat(MatchDecision.matches(MatchingMode.EXACT, true, true, true)).isTrue();
        assertThat(MatchDecision.matches(MatchingMode.EXACT, true, true, false)).isFalse();
        assertThat(MatchDecision.matches(MatchingMode.EXACT, false, true, true)).isFalse();
    }

    @Test void unknownModeFallsBackToExact() {
        assertThat(MatchingMode.parse("something-unsafe")).isEqualTo(MatchingMode.EXACT);
    }
}
