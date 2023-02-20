/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.ProcessingKey;
import org.junit.jupiter.api.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ProcessingInstructionHelper}.
 *
 * @author Steven van Beelen
 */
class ProcessingInstructionHelperTest {

    private static final long EXPECTED_VALUE = 1729L;
    private static final MetaDataValue TEST_META_DATA_VALUE = MetaDataValue.newBuilder()
                                                                           .setNumberValue(EXPECTED_VALUE)
                                                                           .build();

    @Test
    void priorityDefaultsToZero() {
        assertEquals(0L, ProcessingInstructionHelper.priority(Collections.emptyList()));
    }

    @Test
    void priority() {
        ProcessingInstruction testProcessingInstruction =
                ProcessingInstruction.newBuilder()
                                     .setKey(ProcessingKey.PRIORITY)
                                     .setValue(TEST_META_DATA_VALUE)
                                     .build();
        assertEquals(EXPECTED_VALUE,
                     ProcessingInstructionHelper.priority(Collections.singletonList(testProcessingInstruction)));
    }

    @Test
    void numberOfResultsDefaultsToZero() {
        assertEquals(1L, ProcessingInstructionHelper.numberOfResults(Collections.emptyList()));
    }

    @Test
    void numberOfResults() {
        ProcessingInstruction testProcessingInstruction =
                ProcessingInstruction.newBuilder()
                                     .setKey(ProcessingKey.NR_OF_RESULTS)
                                     .setValue(TEST_META_DATA_VALUE)
                                     .build();
        assertEquals(EXPECTED_VALUE,
                     ProcessingInstructionHelper.numberOfResults(Collections.singletonList(testProcessingInstruction)));
    }

    @Test
    void timeoutDefaultsToZero() {
        assertEquals(0L, ProcessingInstructionHelper.timeout(Collections.emptyList()));
    }

    @Test
    void timeoutDefaults() {
        ProcessingInstruction testProcessingInstruction =
                ProcessingInstruction.newBuilder()
                                     .setKey(ProcessingKey.TIMEOUT)
                                     .setValue(TEST_META_DATA_VALUE)
                                     .build();
        assertEquals(EXPECTED_VALUE,
                     ProcessingInstructionHelper.timeout(Collections.singletonList(testProcessingInstruction)));
    }
}