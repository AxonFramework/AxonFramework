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