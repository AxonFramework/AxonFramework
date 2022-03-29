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

package org.axonframework.monitoring;

import org.axonframework.messaging.Message;

import javax.annotation.Nonnull;

/**
 * A message monitor that returns a NoOp message callback
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public enum NoOpMessageMonitor implements MessageMonitor<Message<?>> {

    /**
     * Singleton instance of a {@link NoOpMessageMonitor}.
     */
    INSTANCE;

    /**
     * Returns the instance of {@code {@link NoOpMessageMonitor}}.
     * This method is a convenience method, which can be used as a lambda expression
     *
     * @return the instance of {@code {@link NoOpMessageMonitor}}
     */
    @SuppressWarnings("SameReturnValue")
    public static NoOpMessageMonitor instance() {
        return INSTANCE;
    }

    @Override
    public MonitorCallback onMessageIngested(@Nonnull Message<?> message) {
        return NoOpMessageMonitorCallback.INSTANCE;
    }

}
