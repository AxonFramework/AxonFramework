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

package org.axonframework.eventhandling.monitoring;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.monitoring.MessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * An {@link EventHandlingComponent} that monitors the handling of events using a {@link MessageMonitor}.
 * <p>
 * It delegates the actual event handling to another {@link EventHandlingComponent} while monitoring the events
 * processed.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
// TODO #3595 - The MessageMonitor should align with the new async-native API.
public class MonitoringEventHandlingComponent extends DelegatingEventHandlingComponent {

    private final Logger logger = LoggerFactory.getLogger(MonitoringEventHandlingComponent.class);

    private final MessageMonitor<? super EventMessage> messageMonitor;

    /**
     * Constructs the component with given {@code delegate} to receive calls.
     *
     * @param delegate       The instance to delegate calls to.
     * @param messageMonitor The {@link MessageMonitor} to monitor the events processed by this component.
     */
    public MonitoringEventHandlingComponent(@Nonnull MessageMonitor<? super EventMessage> messageMonitor,
                                            @Nonnull EventHandlingComponent delegate
    ) {
        super(delegate);
        this.messageMonitor = Objects.requireNonNull(messageMonitor, "MessageMonitor may not be null");
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                               @Nonnull ProcessingContext context) {
        var monitorCallback = messageMonitor.onMessageIngested(event);
        return delegate.handle(event, context)
                       .whenComplete(() -> {
                           try {
                               monitorCallback.reportSuccess();
                           } catch (Exception e) {
                               logger.warn("An exception occurred while reporting success of event handling", e);
                           }
                       })
                       .onErrorContinue(ex -> {
                           try {
                                 monitorCallback.reportFailure(ex);
                            } catch (Exception e) {
                                 logger.warn("An exception occurred while reporting failure of event handling", e);
                           }
                           return MessageStream.failed(ex);
                       }).ignoreEntries().cast();
    }
}
