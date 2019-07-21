package org.axonframework.queryhandling.repository;

import java.time.Duration;
import java.time.Instant;

class RepositoryTestUtil {
    public static boolean javaPersistenceTimeDiscrepancy(Instant instant1, Instant instant2, long deltaNanos) {
        return Duration.between(
                instant1,
                instant2
        ).toNanos() <= deltaNanos;
    }
}
