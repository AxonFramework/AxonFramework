/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.messaging.correlation;

import org.axonframework.messaging.Message;

import java.util.Map;

/**
 * Object defining the data from a Message that should be attached as correlation data to messages generated as result
 * of the processing of that message.
 *
 * @author Allard Buijze
 * @since 2.3
 */
@FunctionalInterface
public interface CorrelationDataProvider {

    /**
     * Provides a map with the entries to attach as correlation data to generated messages while processing given
     * {@code message}.
     * <p/>
     * This method should not return {@code null}. Any exception thrown from this method might interfere with rolling
     * back a transaction. Therefore, by default exceptions are caught, ignoring the correlation data that should have
     * been added.
     *
     * @param message The message to define correlation data for
     * @return the data to attach as correlation data to generated messages
     */
    Map<String, ?> correlationDataFor(Message<?> message);
}
