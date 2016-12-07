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

package org.axonframework.messaging;

import org.axonframework.eventsourcing.eventstore.TrackingToken;

/**
 * Interface for a source of {@link Message messages} that processors can track.
 *
 * @author Rene de Waele
 */
public interface StreamableMessageSource<M extends Message<?>> {

    /**
     * Open a stream containing all messages since given tracking token. Pass a {@code trackingToken} of {@code null} to
     * open a stream containing all available messages. Note that the returned stream is <em>infinite</em>, so beware of
     * applying terminal operations to the returned stream.
     *
     * @param trackingToken object containing the position in the stream or {@code null} to open a stream containing all
     *                      messages
     * @return a stream of messages since the given trackingToken
     */
    MessageStream<M> openStream(TrackingToken trackingToken);

}
