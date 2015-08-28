/*
 * Copyright (c) 2010-2014. Axon Framework
 *
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

package org.axonframework.eventhandling;

import java.util.List;

/**
 * Interface describing a mechanism that listens for the results of events being processed. When subscribed to an
 * object implementing {@link org.axonframework.eventhandling.EventProcessingMonitorSupport}, the monitor will be
 * notified when events have been processed.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public interface EventProcessingMonitor {

    /**
     * Invoked when one or more events have been successfully processed by the instance it was subscribed to.
     *
     * @param eventMessages The messages that have been successfully processed
     */
    void onEventProcessingCompleted(List<? extends EventMessage> eventMessages);

    /**
     * Invoked when one or more events have failed processing by the instance it was subscribed to.
     *
     * @param eventMessages The message that failed
     * @param cause         The cause of the failure
     */
    void onEventProcessingFailed(List<? extends EventMessage> eventMessages, Throwable cause);
}
