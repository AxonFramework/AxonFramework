/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling.tokenstore.jdbc;

/**
 * Schema of an token entry to be stored using Jdbc.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class TokenSchema {

    private final String tokenTable;
    private final String processorNameColumn;
    private final String segmentColumn;
    private final String tokenColumn;
    private final String tokenTypeColumn;
    private final String timestampColumn;
    private final String ownerColumn;

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
        this.ownerColumn = builder.ownerColumn;
    }

    /**
     * Returns a new {@link Builder} initialized with default settings.
     *
     * @return a new builder for the event schema
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the name of the token entry table.
     *
     * @return the name of the token entry table
     */
    public String tokenTable() {
        return tokenTable;
    }

    /**
     * Returns the name of the column containing the name of the processor to which the token belongs.
     *
     * @return the name of the column containing the name of the processor owning the token
     */
    public String processorNameColumn() {
        return processorNameColumn;
    }

    /**
     * Returns the name of the column containing the segment of the processor to which the token belongs.
     *
     * @return the name of the column containing the segment of the processor owning the token
     */
    public String segmentColumn() {
        return segmentColumn;
    }

    /**
     * Returns the name of the column containing the serialized token.
     *
     * @return the name of the column containing the serialized token
     */
    public String tokenColumn() {
        return tokenColumn;
    }

    /**
     * Returns the name of the column containing the name of the type to which the token should be deserialized.
     *
     * @return the name of the column containing the token type
     */
    public String tokenTypeColumn() {
        return tokenTypeColumn;
    }

    /**
     * Returns the name of the column containing the timestamp of the token (the time this token was last saved).
     *
     * @return the name of the column containing the token timestamp
     */
    public String timestampColumn() {
        return timestampColumn;
    }

    /**
     * Returns the name of the column containing the name of the machine that is currently the owner of this token.
     *
     * @return the name of the column containing the name of the owner node
     * @deprecated in favor of {@link #ownerColumn}
     */
    @Deprecated
    public String ownerColum() {
        return ownerColumn();
    }

    /**
     * Returns the name of the column containing the name of the machine that is currently the owner of this token.
     *
     * @return the name of the column containing the name of the owner node
     */
    public String ownerColumn() {
        return ownerColumn;
    }

    /**
     * Builder for an {@link TokenSchema} that gets initialized with default values.
     */
    public static class Builder {

        private String tokenTable = "TokenEntry";
        private String processorNameColumn = "processorName";
        private String segmentColumn = "segment";
        private String tokenColumn = "token";
        private String tokenTypeColumn = "tokenType";
        private String timestampColumn = "timestamp";
        private String ownerColumn = "owner";

        /**
         * Sets the name of the token entry table. Defaults to 'TokenEntry'.
         *
         * @param tokenTable the token table name
         * @return the modified Builder instance
         */
        public Builder setTokenTable(String tokenTable) {
            this.tokenTable = tokenTable;
            return this;
        }

        /**
         * Sets the name of the processor name column. Defaults to 'processorName'.
         *
         * @param columnName the name of the column
         * @return the modified Builder instance
         */
        public Builder setProcessorNameColumn(String columnName) {
            this.processorNameColumn = columnName;
            return this;
        }

        /**
         * Sets the name of the processor segment column. Defaults to 'segment'.
         *
         * @param columnName the name of the column
         * @return the modified Builder instance
         */
        public Builder setSegmentColumn(String columnName) {
            this.segmentColumn = columnName;
            return this;
        }

        /**
         * Sets the name of the serialized token column. Defaults to 'token'.
         *
         * @param columnName the name of the column
         * @return the modified Builder instance
         */
        public Builder setTokenColumn(String columnName) {
            this.tokenColumn = columnName;
            return this;
        }

        /**
         * Sets the name of the token type column. Defaults to 'tokenType'.
         *
         * @param columnName the name of the column
         * @return the modified Builder instance
         */
        public Builder setTokenTypeColumn(String columnName) {
            this.tokenTypeColumn = columnName;
            return this;
        }

        /**
         * Sets the name of the timestamp column. Defaults to 'timestamp'.
         *
         * @param columnName the name of the column
         * @return the modified Builder instance
         */
        public Builder setTimestampColumn(String columnName) {
            this.timestampColumn = columnName;
            return this;
        }

        /**
         * Sets the name of the owner column. Defaults to 'owner'.
         *
         * @param columnName the name of the column
         * @return the modified Builder instance
         * @deprecated in favor of {@link #setOwnerColumn(String)}
         */
        @Deprecated
        public Builder setOwnerColum(String columnName) {
            return setOwnerColumn(columnName);
        }

        /**
         * Sets the name of the owner column. Defaults to 'owner'.
         *
         * @param columnName the name of the column
         * @return the modified Builder instance
         */
        public Builder setOwnerColumn(String columnName) {
            this.ownerColumn = columnName;
            return this;
        }

        /**
         * Builds a new {@link TokenSchema} from builder values.
         *
         * @return TokenSchema from this builder
         */
        public TokenSchema build() {
            return new TokenSchema(this);
        }
    }
}
