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

package org.axonframework.messaging.eventhandling.processing.streaming.token;

import org.axonframework.conversion.TestConverter;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests conversion capabilities of {@link ReplayToken}.
 *
 * @author JohT
 */
class ReplayTokenConversionTest {

    static Collection<TestConverter> converters() {
        return TestConverter.all();
    }

    @MethodSource("converters")
    @ParameterizedTest
    void tokenShouldBeConverted(TestConverter converter) {
        // given...
        TrackingToken innerToken = GapAwareTrackingToken.newInstance(10, Collections.singleton(9L));
        TrackingToken expected = ReplayToken.createReplayToken(innerToken);
        // when...
        TrackingToken actual = converter.serializeDeserialize(expected);
        // then...
        assertThat(actual).isEqualTo(expected);
    }

    @MethodSource("converters")
    @ParameterizedTest
    void tokenWithContextShouldBeConverted(TestConverter converter) {
        // given...
        TrackingToken innerToken = GapAwareTrackingToken.newInstance(10, Collections.singleton(9L));
        byte[] expectedContext = converter.getConverter().convert("test", byte[].class);
        TrackingToken expected = ReplayToken.createReplayToken(innerToken, null, expectedContext);
        // when...
        TrackingToken actual = converter.serializeDeserialize(expected);
        // then...
        assertThat(actual).isEqualTo(expected);
    }
}