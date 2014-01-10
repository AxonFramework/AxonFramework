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

/**
 * Interface indicating that the implementing class is capable of notifying monitors when event processing completes.
 * <p/>
 * This interface should be implemented by all event handling components that are capable of processing events
 * asynchronously.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public interface EventProcessingMonitorSupport {

    /**
     * Subscribes the given <code>monitor</code>. If the monitor is already subscribed, nothing happens.
     *
     * @param monitor The monitor to subscribe
     */
    void subscribeEventProcessingMonitor(EventProcessingMonitor monitor);

    /**
     * Unsubscribed the given <code>monitor</code>. If the monitor was not subscribed, or was already unsubscribed,
     * nothing happens.
     *
     * @param monitor The monitor to unsubscribe
     */
    void unsubscribeEventProcessingMonitor(EventProcessingMonitor monitor);
}
