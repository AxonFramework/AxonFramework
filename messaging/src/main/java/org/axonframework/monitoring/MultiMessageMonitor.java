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

package org.axonframework.monitoring;

import org.axonframework.common.Assert;
import org.axonframework.messaging.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Delegates messages and callbacks to the given list of message monitors
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public class MultiMessageMonitor<T extends Message<?>> implements MessageMonitor<T> {

    private final List<MessageMonitor<? super T>> messageMonitors;

    /**
     * Initialize a message monitor with the given <name>messageMonitors</name>
     *
     * @param messageMonitors the list of event monitors to delegate to
     */
    @SafeVarargs
    public MultiMessageMonitor(MessageMonitor<? super T>... messageMonitors) {
        this(Arrays.asList(messageMonitors));
    }

    /**
     * Initialize a message monitor with the given list of <name>messageMonitors</name>
     *
     * @param messageMonitors the list of event monitors to delegate to
     */
    public MultiMessageMonitor(List<MessageMonitor<? super T>> messageMonitors) {
        Assert.notNull(messageMonitors, () -> "MessageMonitor list may not be null");
        this.messageMonitors = new ArrayList<>(messageMonitors);
    }

    /**
     * Calls the message monitors with the given message and returns a callback that will trigger all the message
     * monitor callbacks
     *
     * @param message the message to delegate to the message monitors
     * @return the callback that will trigger all the message monitor callbacks
     */
    @Override
    public MonitorCallback onMessageIngested(@Nonnull T message) {
        final List<MonitorCallback> monitorCallbacks = messageMonitors.stream()
                                                                      .map(messageMonitor -> messageMonitor.onMessageIngested(
                                                                              message))
                                                                      .collect(Collectors.toList());

        return new MonitorCallback() {
            @Override
            public void reportSuccess() {
                monitorCallbacks.forEach(MonitorCallback::reportSuccess);
            }

            @Override
            public void reportFailure(Throwable cause) {
                monitorCallbacks.forEach(resultCallback -> resultCallback.reportFailure(cause));
            }

            @Override
            public void reportIgnored() {
                monitorCallbacks.forEach(MonitorCallback::reportIgnored);
            }
        };
    }
}
