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

/**
 * Exception indicating that an aggregate has been blacklisted by the DisruptorCommandBus. The cause of the blacklisting
 * is provided in the {@link #getCause()} of the exception.
 * <p/>
 * Aggregates are blacklisted when the DisruptorCommandBus cannot guarantee that the state of the aggregate in its cache
 * is correct. A cleanup notification will be handled by the disruptor to recover from this, by clearing the cached data
 * of the aggregate. After the cached information has been cleared, the blacklist status is removed.
 * <p/>
 * It is generally safe to retry any commands that resulted in this exception, unless the cause is clearly
 * non-transient.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AggregateBlacklistedException extends AggregateStateCorruptedException {

    private static final long serialVersionUID = -6223847897300183682L;

    /**
     * Initializes the exception with given {@code aggregateIdentifier}, given explanatory {@code message} and {@code
     * cause}.
     *
     * @param aggregateIdentifier The identifier of the blacklisted aggregate
     * @param message             The message explaining why the blacklisting occurred
     * @param cause               The cause of the blacklist
     */
    public AggregateBlacklistedException(String aggregateIdentifier, String message, Throwable cause) {
        super(aggregateIdentifier, message, cause);
    }
}
