/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.replay.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.annotation.HandlerAttributes;
import org.axonframework.messaging.core.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;
import org.axonframework.messaging.core.annotation.WrappedMessageHandlingMember;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.annotation.EventHandlingMember;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import java.lang.reflect.Member;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.singletonMap;

/**
 * An implementation of the {@link HandlerEnhancerDefinition} that is used for {@link AllowReplay} annotated
 * {@link EventHandlingMember event handling methods} .
 *
 * @author Allard Buijze
 * @since 3.2.0
 */
public class ReplayAwareMessageHandlerWrapper implements HandlerEnhancerDefinition {

    private static final Map<String, Object> DEFAULT_SETTING = singletonMap("allowReplay", Boolean.TRUE);

    @Override
    public @Nonnull
    <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        boolean isReplayAllowed = (boolean) original
                .attribute(HandlerAttributes.ALLOW_REPLAY)
                .orElseGet(() -> original.unwrap(Member.class)
                                         .map(Member::getDeclaringClass)
                                         .map(c -> AnnotationUtils.findAnnotationAttributes(c, AllowReplay.class)
                                                                  .orElse(DEFAULT_SETTING))
                                         .orElse(DEFAULT_SETTING).get("allowReplay")
                );
        if (!isReplayAllowed && original instanceof EventHandlingMember<T> eventHandlingMember) {
            return new ReplayBlockingMessageHandlingMember<>(eventHandlingMember);
        }
        return original;
    }

    private static class ReplayBlockingMessageHandlingMember<T>
            extends WrappedMessageHandlingMember<T>
            implements EventHandlingMember<T> {

        private final EventHandlingMember<T> delegate;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public static final Optional<Boolean> NO_REPLAY = Optional.of(Boolean.FALSE);

        public ReplayBlockingMessageHandlingMember(EventHandlingMember<T> original) {
            super(original);
            this.delegate = original;
        }

        @Override
        public String eventName() {
            return delegate.eventName();
        }

        @Override
        public MessageStream<?> handle(@Nonnull Message message,
                                       @Nonnull ProcessingContext context,
                                       @Nullable T target) {
            Optional<TrackingToken> optionalToken = TrackingToken.fromContext(context);
            if (optionalToken.isPresent() && ReplayToken.isReplay(optionalToken.get())) {
                return MessageStream.empty();
            }
            return super.handle(message, context, target);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> Optional<R> attribute(String attributeKey) {
            if (HandlerAttributes.ALLOW_REPLAY.equals(attributeKey)) {
                return (Optional<R>) NO_REPLAY;
            }
            return super.attribute(attributeKey);
        }
    }
}
