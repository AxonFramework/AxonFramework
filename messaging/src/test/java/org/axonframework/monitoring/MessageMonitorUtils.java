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

package org.axonframework.monitoring;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.monitoring.MessageMonitor.MonitorCallback;

import java.util.function.Function;

public class MessageMonitorUtils {

    @FunctionalInterface
    public interface MonitorCallBackProvider extends Function<CommandMessage, MonitorCallback> {

    }

    public static MessageMonitor<CommandMessage> commandMessageMonitor(final MonitorCallBackProvider callbackProvider) {
        return new MessageMonitor<>() {
            @Override
            public MonitorCallback onMessageIngested(@Nonnull CommandMessage message) {
                return callbackProvider.apply(message);
            }

            @Override
            public String toString() {
                return "anonymous CommandMessageMonitor";
            }
        };
    }

    private MessageMonitorUtils() {
    }
}
