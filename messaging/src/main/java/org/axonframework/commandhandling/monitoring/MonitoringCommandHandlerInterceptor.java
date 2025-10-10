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

package org.axonframework.commandhandling.monitoring;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MessageMonitorUtils;

/**
 * A {@link MessageHandlerInterceptor} for {@link CommandMessage} that intercepts the {@link MessageHandlerInterceptorChain}
 * to register the {@link org.axonframework.monitoring.MessageMonitor.MonitorCallback} functions on the
 * {@link ProcessingContext} hooks. Invoked by the {@link org.axonframework.commandhandling.InterceptingCommandBus}.
 * <p/>
 * Note: Commands are only monitored on handling, not dispatching.
 */
public class MonitoringCommandHandlerInterceptor implements MessageHandlerInterceptor<CommandMessage> {

    private final MessageMonitor<? super CommandMessage> messageMonitor;

    /**
     * Constructs a {@link MonitoringCommandHandlerInterceptor} using the given {@link MessageMonitor}.
     * @param messageMonitor used for reporting
     */
    public MonitoringCommandHandlerInterceptor(MessageMonitor<? super CommandMessage> messageMonitor) {
        this.messageMonitor = messageMonitor;
    }

    @Nonnull
    @Override
    public MessageStream<?> interceptOnHandle(@Nonnull CommandMessage message,
                                              @Nonnull ProcessingContext context,
                                              @Nonnull MessageHandlerInterceptorChain<CommandMessage> interceptorChain) {
        MessageMonitorUtils.registerMonitorCallback(context, messageMonitor, message);
        return interceptorChain.proceed(message, context);
    }
}
