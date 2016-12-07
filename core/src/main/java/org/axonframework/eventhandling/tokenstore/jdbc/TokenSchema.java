/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.tokenstore.jdbc;

/**
 * @author Rene de Waele
 */
public class TokenSchema {

    private final String tokenTable, processorNameColumn, segmentColumn, tokenColumn, tokenTypeColumn, timestampColumn,
            ownerColum;

    /**
     * Initializes the default TokenSchema
     */
    public TokenSchema() {
        this(builder());
    }

    private TokenSchema(Builder builder) {
        this.tokenTable = builder.tokenTable;
        this.processorNameColumn = builder.processorNameColumn;
        this.segmentColumn = builder.segmentColumn;
        this.tokenColumn = builder.tokenColumn;
        this.tokenTypeColumn = builder.tokenTypeColumn;
        this.timestampColumn = builder.timestampColumn;
        this.ownerColum = builder.ownerColum;
    }

    /**
     * Returns a new {@link Builder} initialized with default settings.
     *
     * @return a new builder for the event schema
     */
    public static Builder builder() {
        return new Builder();
    }

    public String tokenTable() {
        return tokenTable;
    }

    public String processorNameColumn() {
        return processorNameColumn;
    }

    public String segmentColumn() {
        return segmentColumn;
    }

    public String tokenColumn() {
        return tokenColumn;
    }

    public String tokenTypeColumn() {
        return tokenTypeColumn;
    }

    public String timestampColumn() {
        return timestampColumn;
    }

    public String ownerColum() {
        return ownerColum;
    }

    public static class Builder {
        private String tokenTable = "TokenEntry";
        private String processorNameColumn = "processorName";
        private String segmentColumn = "segment";
        private String tokenColumn = "token";
        private String tokenTypeColumn = "tokenType";
        private String timestampColumn = "timestamp";
        private String ownerColum = "owner";

        public Builder setTokenTable(String tokenTable) {
            this.tokenTable = tokenTable;
            return this;
        }

        public Builder setProcessorNameColumn(String processorNameColumn) {
            this.processorNameColumn = processorNameColumn;
            return this;
        }

        public Builder setSegmentColumn(String segmentColumn) {
            this.segmentColumn = segmentColumn;
            return this;
        }

        public Builder setTokenColumn(String tokenColumn) {
            this.tokenColumn = tokenColumn;
            return this;
        }

        public Builder setTokenTypeColumn(String tokenTypeColumn) {
            this.tokenTypeColumn = tokenTypeColumn;
            return this;
        }

        public Builder setTimestampColumn(String timestampColumn) {
            this.timestampColumn = timestampColumn;
            return this;
        }

        public Builder setOwnerColum(String ownerColum) {
            this.ownerColum = ownerColum;
            return this;
        }

        public TokenSchema build() {
            return new TokenSchema(this);
        }
    }


}
