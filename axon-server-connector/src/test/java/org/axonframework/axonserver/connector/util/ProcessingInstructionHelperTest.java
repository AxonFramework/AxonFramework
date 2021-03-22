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
    void testPriorityDefaultsToZero() {
        assertEquals(0L, ProcessingInstructionHelper.priority(Collections.emptyList()));
    }

    @Test
    void testPriority() {
        ProcessingInstruction testProcessingInstruction =
                ProcessingInstruction.newBuilder()
                                     .setKey(ProcessingKey.PRIORITY)
                                     .setValue(TEST_META_DATA_VALUE)
                                     .build();
        assertEquals(EXPECTED_VALUE,
                     ProcessingInstructionHelper.priority(Collections.singletonList(testProcessingInstruction)));
    }

    @Test
    void testNumberOfResultsDefaultsToZero() {
        assertEquals(1L, ProcessingInstructionHelper.numberOfResults(Collections.emptyList()));
    }

    @Test
    void testNumberOfResults() {
        ProcessingInstruction testProcessingInstruction =
                ProcessingInstruction.newBuilder()
                                     .setKey(ProcessingKey.NR_OF_RESULTS)
                                     .setValue(TEST_META_DATA_VALUE)
                                     .build();
        assertEquals(EXPECTED_VALUE,
                     ProcessingInstructionHelper.numberOfResults(Collections.singletonList(testProcessingInstruction)));
    }

    @Test
    void testTimeoutDefaultsToZero() {
        assertEquals(0L, ProcessingInstructionHelper.timeout(Collections.emptyList()));
    }

    @Test
    void testTimeoutDefaults() {
        ProcessingInstruction testProcessingInstruction =
                ProcessingInstruction.newBuilder()
                                     .setKey(ProcessingKey.TIMEOUT)
                                     .setValue(TEST_META_DATA_VALUE)
                                     .build();
        assertEquals(EXPECTED_VALUE,
                     ProcessingInstructionHelper.timeout(Collections.singletonList(testProcessingInstruction)));
    }
}