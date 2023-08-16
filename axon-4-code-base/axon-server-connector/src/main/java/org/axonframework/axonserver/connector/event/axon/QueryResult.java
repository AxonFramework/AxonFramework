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
package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.event.EventQueryResultEntry;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Single result row from a Query to the AxonServer.
 * <p>
 * When applying aggregation functions in the query (e.g., min/max/groupBy/count/avg) you will get values for the
 * identifier. Same identifier may occur more than once as the results get updated.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class QueryResult {

    private final EventQueryResultEntry entry;

    /**
     * Constructs a QueryResult from the given {@code entry}.
     *
     * @param entry the entry to wrap
     */
    public QueryResult(EventQueryResultEntry entry) {
        this.entry = entry;
    }

    /**
     * Retrieve the column information referring to the given {@code name}.
     *
     * @param name the column name to retrieve information for
     * @return the column information referring to the given {@code name}
     */
    public Object get(String name) {
        return entry.getValue(name);
    }

    /**
     * Returns the identifiers that uniquely identify this result entry. When multiple entries have equal identifier,
     * the second result entry represents an updated version of the first.
     *
     * @return the identifiers of this query result
     */
    public List<Object> getIdentifiers() {
        return entry.getIdentifiers();
    }

    /**
     * Returns the list of values to use to compare the order of this entry to other entries. Two entries with the same
     * sort values cannot be considered equal, but rather don't have a defined order between them.
     *
     * @return the sort values of this query result
     */
    public List<Object> getSortValues() {
        return entry.getSortValues();
    }

    /**
     * The list of columns returned by the query. Will return the same value for all result entries of the same query.
     *
     * @return the names of the columns returned in this entry
     */
    public List<String> getColumns() {
        return entry.columns();
    }

    /**
     * Returns the wrapped entry as returned by the AxonServer Java Connector.
     *
     * @return the wrapped entry as returned by the AxonServer Java Connector
     */
    public EventQueryResultEntry getQueryResultEntry() {
        return entry;
    }

    @Override
    public String toString() {
        return getColumns().stream().map(col -> col + "=" + get(col)).collect(Collectors.joining(","));
    }
}
