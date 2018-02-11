/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.replay;

import org.axonframework.eventhandling.ReplayStatus;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

public class ReplayParameterResolverFactory implements ParameterResolverFactory {
    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        if (ReplayStatus.class.isAssignableFrom(parameters[parameterIndex].getType())) {
            return new ReplayParameterResolver();
        }
        return null;
    }

    private class ReplayParameterResolver implements ParameterResolver {
        @Override
        public Object resolveParameterValue(Message message) {
            return message instanceof TrackedEventMessage
                    && ((TrackedEventMessage) message).trackingToken() instanceof ReplayToken
                    ? ReplayStatus.REPLAY
                    : ReplayStatus.REGULAR;
        }

        @Override
        public boolean matches(Message message) {
            return true;
        }
    }
}
