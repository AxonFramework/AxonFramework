/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.saga.repository.jdbc;

/**
 * SagaSchema allows specification of custom storage locations for the saga repositories.
 * <p/>
 * Usage depends on the underlying database - in general the form of "schema.tablename" should be sufficient.
 *
 * @author Jochen Munz
 * @since 2.4
 */
public class SagaSchema {

    private static final String DEFAULT_SAGA_ENTRY_TABLE = "SagaEntry";
    private static final String DEFAULT_ASSOC_VALUE_ENTRY_TABLE = "AssociationValueEntry";

    private final String sagaEntryTable;
    private final String associationValueEntryTable;

    /**
     * Initialize SagaSchema with default values.
     */
    public SagaSchema() {
        this(DEFAULT_SAGA_ENTRY_TABLE, DEFAULT_ASSOC_VALUE_ENTRY_TABLE);
    }

    /**
     * Initialize SagaSchema with custom locations for event entry tables.
     *
     * @param sagaEntryTable              The name of the entry table
     * @param associationValueEntryTable  The name of the association value table
     */
    public SagaSchema(String sagaEntryTable, String associationValueEntryTable) {
        this.sagaEntryTable = sagaEntryTable;
        this.associationValueEntryTable = associationValueEntryTable;
    }

    /**
     * Returns the name of the associationValueEntry table
     *
     * @return the name of the associationValueEntry table
     */
    public String associationValueEntryTable() {
        return associationValueEntryTable;
    }

    /**
     * Returns the name of the sagaEntry table
     *
     * @return the name of the sagaEntry table
     */
    public String sagaEntryTable() {
        return sagaEntryTable;
    }
}
