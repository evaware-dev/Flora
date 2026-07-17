package sweetie.evaware.flora.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchConfigTest {
    @Test
    void defaultsAreValid() {
        DispatchConfig config = DispatchConfig.defaults();

        assertTrue(config.asyncLaneCount() > 0);
        assertTrue(config.parallelLaneCount() > 0);
        assertTrue(Integer.bitCount(config.asyncQueueCapacity()) == 1);
        assertTrue(Integer.bitCount(config.parallelQueueCapacity()) == 1);
    }

    @Test
    void rejectsInvalidQueueCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new DispatchConfig(1, 1, 15, 16, 0, 0, 1));
    }
}
