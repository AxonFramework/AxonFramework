/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.eventhandling.deadletter.jdbc;

import org.axonframework.common.AxonConfigurationException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DeadLetterSchema}.
 *
 * @author Steven van Beelen
 */
class DeadLetterSchemaTest {

    public static final String TEST_COLUMN_NAME = "some-name";

    @Test
    void buildWithDeadLetterTableReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .deadLetterTable(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.deadLetterTable());
    }

    @Test
    void buildWithNullDeadLetterTableThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.deadLetterTable(null));
    }

    @Test
    void buildWithEmptyDeadLetterTableThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.deadLetterTable(""));
    }

    @Test
    void buildWithDeadLetterIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .deadLetterIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.deadLetterIdentifierColumn());
    }

    @Test
    void buildWithNullDeadLetterIdentifierColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.deadLetterIdentifierColumn(null));
    }

    @Test
    void buildWithEmptyDeadLetterIdentifierColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.deadLetterIdentifierColumn(""));
    }

    @Test
    void buildWithProcessingGroupColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .processingGroupColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.processingGroupColumn());
    }

    @Test
    void buildWithNullProcessingGroupColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.processingGroupColumn(null));
    }

    @Test
    void buildWithEmptyProcessingGroupColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.processingGroupColumn(""));
    }

    @Test
    void buildWithSequenceIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .sequenceIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.sequenceIdentifierColumn());
    }

    @Test
    void buildWithNullSequenceIdentifierColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.sequenceIdentifierColumn(null));
    }

    @Test
    void buildWithEmptySequenceIdentifierColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.sequenceIdentifierColumn(""));
    }

    @Test
    void buildWithSequenceIndexColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .sequenceIndexColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.sequenceIndexColumn());
    }

    @Test
    void buildWithNullSequenceIndexColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.sequenceIndexColumn(null));
    }

    @Test
    void buildWithEmptySequenceIndexColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.sequenceIndexColumn(""));
    }

    @Test
    void buildWithEventTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .eventTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.eventTypeColumn());
    }

    @Test
    void buildWithNullEventTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.eventTypeColumn(null));
    }

    @Test
    void buildWithEmptyEventTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.eventTypeColumn(""));
    }

    @Test
    void buildWithEventIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .eventIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.eventIdentifierColumn());
    }

    @Test
    void buildWithNullEventIdentifierColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.eventIdentifierColumn(null));
    }

    @Test
    void buildWithEmptyEventIdentifierColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.eventIdentifierColumn(""));
    }

    @Test
    void buildWithTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .typeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.typeColumn());
    }

    @Test
    void buildWithNullTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.typeColumn(null));
    }

    @Test
    void buildWithEmptyTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.typeColumn(""));
    }

    @Test
    void buildWithTimeStampColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .timestampColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.timestampColumn());
    }

    @Test
    void buildWithNullTimeStampColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.timestampColumn(null));
    }

    @Test
    void buildWithEmptyTimeStampColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.timestampColumn(""));
    }

    @Test
    void buildWithPayloadTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .payloadTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.payloadTypeColumn());
    }

    @Test
    void buildWithNullPayloadTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.payloadTypeColumn(null));
    }

    @Test
    void buildWithEmptyPayloadTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.payloadTypeColumn(""));
    }

    @Test
    void buildWithPayloadRevisionColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .payloadRevisionColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.payloadRevisionColumn());
    }

    @Test
    void buildWithNullPayloadRevisionColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.payloadRevisionColumn(null));
    }

    @Test
    void buildWithEmptyPayloadRevisionColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.payloadRevisionColumn(""));
    }

    @Test
    void buildWithPayloadColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .payloadColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.payloadColumn());
    }

    @Test
    void buildWithNullPayloadColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.payloadColumn(null));
    }

    @Test
    void buildWithEmptyPayloadColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.payloadColumn(""));
    }

    @Test
    void buildWithMetaDataColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .metaDataColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.metaDataColumn());
    }

    @Test
    void buildWithNullMetaDataColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.metaDataColumn(null));
    }

    @Test
    void buildWithEmptyMetaDataColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.metaDataColumn(""));
    }

    @Test
    void buildWithAggregateTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .aggregateTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.aggregateTypeColumn());
    }

    @Test
    void buildWithNullAggregateTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.aggregateTypeColumn(null));
    }

    @Test
    void buildWithEmptyAggregateTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.aggregateTypeColumn(""));
    }

    @Test
    void buildWithAggregateIdentifierColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .aggregateIdentifierColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.aggregateIdentifierColumn());
    }

    @Test
    void buildWithNullAggregateIdentifierColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.aggregateIdentifierColumn(null));
    }

    @Test
    void buildWithEmptyAggregateIdentifierColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.aggregateIdentifierColumn(""));
    }

    @Test
    void buildWithSequenceNumberColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .sequenceNumberColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.sequenceNumberColumn());
    }

    @Test
    void buildWithNullSequenceNumberColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.sequenceNumberColumn(null));
    }

    @Test
    void buildWithEmptySequenceNumberColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.sequenceNumberColumn(""));
    }

    @Test
    void buildWithTokenTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .tokenTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.tokenTypeColumn());
    }

    @Test
    void buildWithNullTokenTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.tokenTypeColumn(null));
    }

    @Test
    void buildWithEmptyTokenTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.tokenTypeColumn(""));
    }

    @Test
    void buildWithTokenColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .tokenColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.tokenColumn());
    }

    @Test
    void buildWithNullTokenColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.tokenColumn(null));
    }

    @Test
    void buildWithEmptyTokenColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.tokenColumn(""));
    }

    @Test
    void buildWithEnqueuedAtColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .enqueuedAtColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.enqueuedAtColumn());
    }

    @Test
    void buildWithNullEnqueuedAtColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.enqueuedAtColumn(null));
    }

    @Test
    void buildWithEmptyEnqueuedAtColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.enqueuedAtColumn(""));
    }

    @Test
    void buildWithLastTouchedColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .lastTouchedColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.lastTouchedColumn());
    }

    @Test
    void buildWithNullLastTouchedColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.lastTouchedColumn(null));
    }

    @Test
    void buildWithEmptyLastTouchedColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.lastTouchedColumn(""));
    }

    @Test
    void buildWithProcessingStartedColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .processingStartedColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.processingStartedColumn());
    }

    @Test
    void buildWithNullProcessingStartedColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.processingStartedColumn(null));
    }

    @Test
    void buildWithEmptyProcessingStartedColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.processingStartedColumn(""));
    }

    @Test
    void buildWithCauseTypeColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .causeTypeColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.causeTypeColumn());
    }

    @Test
    void buildWithNullCauseTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.causeTypeColumn(null));
    }

    @Test
    void buildWithEmptyCauseTypeColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.causeTypeColumn(""));
    }

    @Test
    void buildWithCauseMessageColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .causeMessageColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.causeMessageColumn());
    }

    @Test
    void buildWithNullCauseMessageColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.causeMessageColumn(null));
    }

    @Test
    void buildWithEmptyCauseMessageColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.causeMessageColumn(""));
    }

    @Test
    void buildWithDiagnosticsColumnReturnsConfiguredColumnName() {
        DeadLetterSchema result = DeadLetterSchema.builder()
                                                  .diagnosticsColumn(TEST_COLUMN_NAME)
                                                  .build();

        assertEquals(TEST_COLUMN_NAME, result.diagnosticsColumn());
    }

    @Test
    void buildWithNullDiagnosticsColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.diagnosticsColumn(null));
    }

    @Test
    void buildWithEmptyDiagnosticsColumnThrowsAxonConfigurationException() {
        DeadLetterSchema.Builder testBuilder = DeadLetterSchema.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.diagnosticsColumn(""));
    }
}