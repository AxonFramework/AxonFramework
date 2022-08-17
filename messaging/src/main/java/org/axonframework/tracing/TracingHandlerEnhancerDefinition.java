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

package org.axonframework.tracing;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Enhances message handlers with the provided {@link SpanFactory}, wrapping handling of the message in a {@link Span}
 * that is reported to the monitoring tooling.
 * <p>
 * Since {@code EventSourcingHandlers} can be very noisy when loading the aggregate, they can be enabled or disabled
 * separately through constructor configuration.
 *
 * @since 4.6.0
 * @author Mitchell Herrijgers
 */
public class TracingHandlerEnhancerDefinition implements HandlerEnhancerDefinition {

    private final SpanFactory spanFactory;
    private final boolean showEventSourcingHandlers;

    public TracingHandlerEnhancerDefinition(SpanFactory spanFactory, boolean showEventSourcingHandlers) {
        this.spanFactory = spanFactory;
        this.showEventSourcingHandlers = showEventSourcingHandlers;
    }

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        if (!showEventSourcingHandlers && isEventSourcingHandler(original)) {
            return original;
        }

        Optional<Executable> unwrap = original.unwrap(Executable.class);
        if (!unwrap.isPresent()) {
            return original;
        }
        String signature = toMethodSignature(unwrap.get());
        return new WrappedMessageHandlingMember<T>(original) {
            @Override
            public Object handle(@Nonnull Message<?> message, T target) throws Exception {
                String completeName = target == null ? signature : target.getClass().getSimpleName() + "." + signature;
                return spanFactory.createInternalSpan(completeName)
                                  .runCallable(() -> super.handle(message, target));
            }
        };
    }

    private boolean isEventSourcingHandler(MessageHandlingMember<?> original) {
        return original.attribute("EventSourcingHandler.payloadType").isPresent();
    }

    private String toMethodSignature(Executable executable) {
        return String.format("%s(%s)",
                             executable.getName(),
                             Arrays.stream(executable.getParameterTypes()).map(Class::getSimpleName)
                                   .collect(Collectors.joining(",")));
    }
}
