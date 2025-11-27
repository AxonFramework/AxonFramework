/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.streaming.token;

import java.io.Serializable;

/**
 * Marker interface for objects that can be used as context in a {@link ReplayToken}.
 * <p>
 * The replay context provides additional information about why a replay was triggered
 * and can be used by event handlers to make decisions during replay processing.
 * <p>
 * Implementations of this interface should be serializable and, when using Kotlin Serialization,
 * should be annotated with {@code @Serializable} and registered in the {@code SerializersModule}.
 * <p>
 * For simple string-based contexts, use the provided {@link StringReplayContext} implementation.
 *
 * @author Axon Framework
 * @see ReplayToken
 * @see StringReplayContext
 * @since 5.0.0
 */
public interface ReplayContext extends Serializable {
}
