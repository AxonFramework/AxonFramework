/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.common;


import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating the {@link FutureUtils}.
 *
 * @author Jan Galinski
 */
class FutureUtilsTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void runFailingReturnsSuccessfully(boolean expectFailure) {
        Function<Boolean, CompletableFuture<Boolean>> fn = value -> {
            Assert.isTrue(value, () -> "value must be true");
            return CompletableFuture.completedFuture(true);
        };

        var result = FutureUtils.runFailing(() -> fn.apply(expectFailure));

        if (expectFailure) {
            assertThat(result).isCompletedWithValue(true);
        } else {
            assertThatThrownBy(() -> FutureUtils.joinAndUnwrap(result))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("value must be true");
        }
    }
}