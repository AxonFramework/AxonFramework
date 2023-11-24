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

package org.axonframework.eventhandling.replay;

import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.eventhandling.AllowReplay;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.messaging.HandlerAttributes;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import java.lang.reflect.Member;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import static java.util.Collections.singletonMap;

/**
 * An implementation of the {@link org.axonframework.messaging.annotation.HandlerEnhancerDefinition} that is used for
 * {@link org.axonframework.eventhandling.AllowReplay} annotated message handling methods.
 *
 * @author Allard Buijze
 * @since 3.2
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
        if (!isReplayAllowed) {
            return new ReplayBlockingMessageHandlingMember<>(original);
        }
        return original;
    }

    private static class ReplayBlockingMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> {

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public static final Optional<Boolean> NO_REPLAY = Optional.of(Boolean.FALSE);

        public ReplayBlockingMessageHandlingMember(MessageHandlingMember<T> original) {
            super(original);
        }

        @Override
        public Object handleSync(@Nonnull Message<?> message, T target) throws Exception {
            if (ReplayToken.isReplay(message)) {
                return null;
            }
            return super.handleSync(message, target);
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
