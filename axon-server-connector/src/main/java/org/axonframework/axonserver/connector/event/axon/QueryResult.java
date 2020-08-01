/*
 * Copyright (c) 2010-2020. Axon Framework
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

import io.axoniq.axonserver.grpc.event.QueryValue;
import io.axoniq.axonserver.grpc.event.RowResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Single result row from a Query to the AxonServer.
 * <p>
 * When applying aggregation functions in the query (min/max/groupby/count/avg) you will get values for the identifier.
 * Same identifier may occur more than once as the results get updated.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class QueryResult {

    private final static QueryValue DEFAULT = QueryValue.newBuilder().build();
    private final RowResponse rowResponse;
    private final List<String> columns;

    /**
     * Constructs a {@link QueryResult}.
     *
     * @param response the result to base this {@link QueryResult} on
     * @param columns  the {@link List} of column information contained in the given {@code response}
     */
    public QueryResult(RowResponse response, List<String> columns) {
        rowResponse = response;
        this.columns = columns;
    }

    /**
     * Retrieve the column information referring to the given {@code name}.
     * @param name the column name to retrieve information for
     * @return the column information referring to the given {@code name}
     */
    public Object get(String name) {
        return unwrap(rowResponse.getValuesOrDefault(name, DEFAULT));
    }

    public List<Object> getIdentifiers() {
        if (rowResponse.getIdValuesCount() == 0) {
            return null;
        }
        return rowResponse.getIdValuesList().stream().map(this::unwrap).collect(Collectors.toList());
    }

    public List<Object> getSortValues() {
        if (rowResponse.getSortValuesCount() == 0) {
            return null;
        }
        return rowResponse.getSortValuesList().stream().map(this::unwrap).collect(Collectors.toList());
    }

    public List<String> getColumns() {
        return columns;
    }

    private Object unwrap(QueryValue value) {
        switch (value.getDataCase()) {
            case TEXT_VALUE:
                return value.getTextValue();
            case NUMBER_VALUE:
                return value.getNumberValue();
            case BOOLEAN_VALUE:
                return value.getBooleanValue();
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            default:
                return null;
        }
    }

    public String toString() {
        return columns.stream().map(col -> col + "=" + get(col)).collect(Collectors.joining(","));
    }
}
