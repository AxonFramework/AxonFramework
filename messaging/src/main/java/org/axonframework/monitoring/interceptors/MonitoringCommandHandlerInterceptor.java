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

package org.axonframework.monitoring.interceptors;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.monitoring.MessageMonitor;

/**
 * A {@link MessageHandlerInterceptor} for {@link CommandMessage} that intercepts the
 * {@link MessageHandlerInterceptorChain} to register the
 * {@link org.axonframework.monitoring.MessageMonitor.MonitorCallback} functions on the {@link ProcessingContext} hooks.
 * <p/>
 * Invoked by the {@link org.axonframework.commandhandling.interceptors.InterceptingCommandBus}.
 * <p/>
 * Commands are only monitored when handled.
 *
 * @author Jan Galinski
 * @since 5.0.0
 */
public class MonitoringCommandHandlerInterceptor extends AbstractMonitoringInterceptor<CommandMessage>
        implements MessageHandlerInterceptor<CommandMessage> {

    /**
     * Constructs a MonitoringCommandHandlerInterceptor using the given {@link MessageMonitor}.
     *
     * @param messageMonitor used for reporting
     */
    public MonitoringCommandHandlerInterceptor(final @Nonnull MessageMonitor<? super CommandMessage> messageMonitor) {
        super(messageMonitor);
    }

    @Nonnull
    @Override
    public MessageStream<?> interceptOnHandle(@Nonnull CommandMessage message,
                                              @Nonnull ProcessingContext context,
                                              @Nonnull MessageHandlerInterceptorChain<CommandMessage> interceptorChain) {
        registerMonitorCallback(context, message);
        return interceptorChain.proceed(message, context);
    }
}
