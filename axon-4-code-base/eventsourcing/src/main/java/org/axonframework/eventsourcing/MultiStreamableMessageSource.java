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

package org.axonframework.eventsourcing;

import org.axonframework.messaging.StreamableMessageSource;

/**
 * Implementation which allows for tracking processors to process messages from an arbitrary number of sources. The
 * order in which messages from each stream are consumed is configurable but defaults to the oldest message available
 * (using the event's timestamp). When the stream is polled for a specified duration, each stream is called with
 * {@code MultiSourceBlockingStream#hasNextAvailable()} except for the last stream configured by the
 * {@link MultiStreamableMessageSource.Builder#addMessageSource(String, StreamableMessageSource)} or by explicit
 * configuration using {@link MultiStreamableMessageSource.Builder#longPollingSource(String)}. This stream long polls
 * for a fraction of the specified duration before looping through the sources again repeating until the duration has
 * been met. This ensures the highest chance of a consumable message being found.
 *
 * @author Greg Woods
 * @since 4.2
 * @deprecated In favor of {@link org.axonframework.eventhandling.MultiStreamableMessageSource}. This class belongs in
 * the {@code messaging} module instead of the {@code eventsourcing} module.
 */
@Deprecated
public class MultiStreamableMessageSource extends org.axonframework.eventhandling.MultiStreamableMessageSource {

    /**
     * Instantiate a Builder to be able to create an {@link MultiStreamableMessageSource}. The configurable field
     * {@code trackedEventComparator}, which decides which message to process first if there is a choice defaults to the
     * oldest message available (using the event's timestamp).
     *
     * @return a Builder to be able to create a {@link MultiStreamableMessageSource}.
     * @deprecated In favor of {@link org.axonframework.eventhandling.MultiStreamableMessageSource#builder()}. This
     * class belongs in the {@code messaging} module instead of the {@code eventsourcing} module.
     */
    @Deprecated
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link MultiStreamableMessageSource} based on the fields contained in the
     * {@link MultiStreamableMessageSource.Builder}.
     * <p>
     *
     * @param builder the {@link MultiStreamableMessageSource.Builder} used to instantiate a
     *                {@link MultiStreamableMessageSource} instance.
     * @deprecated In favor of
     * {@link
     * org.axonframework.eventhandling.MultiStreamableMessageSource#MultiStreamableMessageSource(org.axonframework.eventhandling.MultiStreamableMessageSource.Builder)}.
     * This class belongs in the {@code messaging} module instead of the {@code eventsourcing} module.
     */
    @Deprecated
    protected MultiStreamableMessageSource(Builder builder) {
        super(builder);
    }

    /**
     * Builder class to instantiate a {@link MultiStreamableMessageSource}. The configurable filed
     * {@code trackedEventComparator}, which decides which message to process first if there is a choice defaults to the
     * oldest message available (using the event's timestamp). The stream on which long polling is done for
     * {@code MultiSourceBlockingStream#hasNextAvailable(int, TimeUnit)} is also configurable.
     * <p>
     *
     * @deprecated In favor of {@link org.axonframework.eventhandling.MultiStreamableMessageSource.Builder}. This class
     * belongs in the {@code messaging} module instead of the {@code eventsourcing} module.
     */
    @Deprecated
    public static class Builder extends org.axonframework.eventhandling.MultiStreamableMessageSource.Builder {

    }
}
