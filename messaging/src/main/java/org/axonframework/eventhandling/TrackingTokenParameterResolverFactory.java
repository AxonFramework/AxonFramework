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

package org.axonframework.eventhandling;


import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * Implementation of a {@link ParameterResolverFactory} that resolves the {@link TrackingToken} of an event message
 * if that message is a {@link TrackedEventMessage}.
 */
public class TrackingTokenParameterResolverFactory implements ParameterResolverFactory {

    private static final TrackingTokenParameterResolver RESOLVER = new TrackingTokenParameterResolver();

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        if (TrackingToken.class.equals(parameters[parameterIndex].getType())) {
            return RESOLVER;
        }
        return null;
    }

    private static class TrackingTokenParameterResolver implements ParameterResolver<TrackingToken> {

        @Override
        public TrackingToken resolveParameterValue(Message<?> message) {
            return unwrap(((TrackedEventMessage) message).trackingToken());
        }

        private TrackingToken unwrap(TrackingToken trackingToken) {
            return WrappedToken.unwrapLowerBound(trackingToken);
        }

        @Override
        public boolean matches(Message<?> message) {
            return message instanceof TrackedEventMessage;
        }
    }
}
