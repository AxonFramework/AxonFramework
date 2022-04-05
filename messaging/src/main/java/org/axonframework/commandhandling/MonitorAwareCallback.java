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

package org.axonframework.commandhandling;

import org.axonframework.monitoring.MessageMonitor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Wrapper for a callback that notifies a Message Monitor of the message execution result.
 *
 * @param <R> the type of result of the command handling
 * @param <C> the type of payload of the command
 */
public class MonitorAwareCallback<C, R> implements CommandCallback<C, R> {

    private final CommandCallback<C, R> delegate;
    private final MessageMonitor.MonitorCallback messageMonitorCallback;

    /**
     * Initialize a callback wrapped around the {@code delegate} which will notify a Message Monitor for the given
     * {@code messageMonitorCallback}.
     *
     * @param delegate               the CommandCallback which is being wrapped, may be {@code null}
     * @param messageMonitorCallback the callback for the Message Monitor
     */
    public MonitorAwareCallback(@Nullable CommandCallback<C, R> delegate,
                                @Nonnull MessageMonitor.MonitorCallback messageMonitorCallback) {
        this.delegate = delegate;
        this.messageMonitorCallback = messageMonitorCallback;
    }

    @Override
    public void onResult(@Nonnull CommandMessage<? extends C> commandMessage,
                         @Nonnull CommandResultMessage<? extends R> commandResultMessage) {
        if (commandResultMessage.isExceptional()) {
            messageMonitorCallback.reportFailure(commandResultMessage.exceptionResult());
        } else {
            messageMonitorCallback.reportSuccess();
        }
        if (delegate != null) {
            delegate.onResult(commandMessage, commandResultMessage);
        }
    }

}
