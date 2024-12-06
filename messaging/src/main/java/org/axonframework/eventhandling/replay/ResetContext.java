/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.eventhandling.replay;

import org.axonframework.messaging.Message;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * A {@link Message} initiating the reset of an Event Handling Component.
 * <p>
 * A payload of {@code P} can be provided to support the reset operation handling this message.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link Message}.
 * @author Steven van Beelen
 * @since 4.4.0
 */
public interface ResetContext<P> extends Message<P> {

    @Override
    ResetContext<P> withMetaData(@Nonnull Map<String, ?> metaData);

    @Override
    ResetContext<P> andMetaData(@Nonnull Map<String, ?> metaData);
}
