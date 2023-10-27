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

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Iterator to process the results of a query. Adds hasNext(timeout, TimeUnit timeUnit) operation to limit the time to
 * wait for the next result.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public interface QueryResultStream extends Iterator<QueryResult>, AutoCloseable {

    /**
     * Validates whether there is a following entry in this stream to consume. This method will wait for 1 second to
     * return.
     *
     * @return {@code true} if there is another entry, {@code false} if there isn't for at least 1 second
     */
    default boolean hasNext() {
        return this.hasNext(1, TimeUnit.SECONDS);
    }

    /**
     * Validates whether there is a following entry in this stream to consume. Uses the given {@code timeout} with the
     * given {@code unit} to wait to respond
     *
     * @return {@code true} if there is another entry, {@code false} if there isn't for at the given duration
     */
    boolean hasNext(int timeout, TimeUnit unit);

    /**
     * Returns the following {@code QueryResult} from this stream. Might return {@code null} if nothing is present
     *
     * @return the following {@code QueryResult} from this stream
     */
    QueryResult next();
}
