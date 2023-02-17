/*
 * Copyright 2023 the original author or authors.
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
package org.axonframework.eventhandling.tokenstore.jdbc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenSchemaTest {

    @Test
    void defaultNamesAreCorrect() {
        TokenSchema tokenSchema = TokenSchema.builder()
            .build();

        assertEquals("TokenEntry", tokenSchema.tokenTable());
        assertEquals("processorName", tokenSchema.processorNameColumn());
        assertEquals("segment", tokenSchema.segmentColumn());
        assertEquals("token", tokenSchema.tokenColumn());
        assertEquals("tokenType", tokenSchema.tokenTypeColumn());
        assertEquals("timestamp", tokenSchema.timestampColumn());
        assertEquals("owner", tokenSchema.ownerColumn());
    }

    @Test
    void modifiedNamesAreCorrect() {
        TokenSchema tokenSchema = TokenSchema.builder()
            .setTokenTable("TokenEntryModified")
            .setProcessorNameColumn("processorNameModified")
            .setSegmentColumn("segmentModified")
            .setTokenColumn("tokenModified")
            .setTokenTypeColumn("tokenTypeModified")
            .setTimestampColumn("timestampModified")
            .setOwnerColumn("ownerModified")
            .build();

        assertEquals("TokenEntryModified", tokenSchema.tokenTable());
        assertEquals("processorNameModified", tokenSchema.processorNameColumn());
        assertEquals("segmentModified", tokenSchema.segmentColumn());
        assertEquals("tokenModified", tokenSchema.tokenColumn());
        assertEquals("tokenTypeModified", tokenSchema.tokenTypeColumn());
        assertEquals("timestampModified", tokenSchema.timestampColumn());
        assertEquals("ownerModified", tokenSchema.ownerColumn());
    }

}
