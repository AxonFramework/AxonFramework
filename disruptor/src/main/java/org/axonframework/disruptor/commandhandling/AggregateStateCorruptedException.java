/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.disruptor.commandhandling;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating that the changes in an aggregate (generated events) are ignored by the DisruptorCommandBus,
 * because it cannot guarantee that these changes have been applied to an aggregate instance with the correct state. The
 * DisruptorCommandBus will automatically recover by clearing cached information of blacklisted aggregates. Because of
 * the asynchronous nature of the DisruptorCommandBus, several commands may fail due to the same corrupt aggregate.
 * <p/>
 * When a corrupt aggregate has been detected, a {@link AggregateBlacklistedException} is thrown. Each subsequent time
 * state changes are applied to a blacklisted aggregate, an AggregateStateCorruptedException is thrown.
 * <p/>
 * It is generally safe to retry any commands that resulted in this exception, unless the cause is clearly
 * non-transient.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AggregateStateCorruptedException extends AxonTransientException {

    private static final long serialVersionUID = 133015394552568435L;

    private final String aggregateIdentifier;

    /**
     * Initializes the exception with given {@code aggregateIdentifier} and given explanatory {@code message}.
     *
     * @param aggregateIdentifier The identifier of the blacklisted aggregate
     * @param message             The message explaining why the blacklisting occurred
     */
    public AggregateStateCorruptedException(String aggregateIdentifier, String message) {
        super(message);
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Initializes the exception with given {@code aggregateIdentifier}, given explanatory {@code message} and {@code
     * cause}.
     *
     * @param aggregateIdentifier The identifier of the blacklisted aggregate
     * @param message             The message explaining why the blacklisting occurred
     * @param cause               The cause of the blacklist
     */
    public AggregateStateCorruptedException(String aggregateIdentifier, String message, Throwable cause) {
        super(message, cause);
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Returns the identifier of the blacklisted aggregate.
     *
     * @return the identifier of the blacklisted aggregate
     */
    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}
