/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.axonframework.eventsourcing.eventstore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Test class validating the {@link FetchResult}.
 *
 * @author Markus Eckstein
 */
class FetchResultTest {
    @Test
    void constructorThrowsWhenLastItemDoesNotEqualCursor() {
        assertThrows(IllegalArgumentException.class, () -> new FetchResult<>(List.of("a", "b"), "c"));
    }

    @Test
    void constructorThrowsWhenLastItemIsDefinedAndCursorIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new FetchResult<>(List.of("a", "b"), null));
    }

    @Test
    void constructorValidatesThatLastItemEqualsCursor() {
        assertDoesNotThrow(() -> new FetchResult<>(List.of("a", "b"), "b"));
    }

    @Test
    void constructorAllowsEmptyItemsWithNullCursor() {
        assertDoesNotThrow(() -> new FetchResult<>(List.of(), null));
    }

    @Test
    void constructorAllowsEmptyItemsWithNonNullCursor() {
        assertDoesNotThrow(() -> new FetchResult<>(List.of(), "a"));
    }

    @Test
    void constructorDoesNotAllowNullItems() {
        assertThrows(NullPointerException.class, () -> new FetchResult<>(null, "a"));
    }
}
