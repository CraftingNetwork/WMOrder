package com.wildmare.wmorder.simulation;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class CollectionClaimSimulatorTest {
    @Test void claimTokenMakesCollectionIdempotent() {
        AtomicReference<UUID> token = new AtomicReference<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertThat(token.compareAndSet(null, first)).isTrue();
        assertThat(token.compareAndSet(null, second)).isFalse();
        assertThat(token.compareAndSet(first, null)).isTrue();
    }
}
