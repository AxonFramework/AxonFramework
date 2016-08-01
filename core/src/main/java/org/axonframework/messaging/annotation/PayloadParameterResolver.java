/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.messaging.annotation;

import org.axonframework.messaging.Message;

public class PayloadParameterResolver implements ParameterResolver {

    private final Class<?> payloadType;

    public PayloadParameterResolver(Class<?> payloadType) {
        this.payloadType = payloadType;
    }

    @Override
    public Object resolveParameterValue(Message message) {
        return message.getPayload();
    }

    @Override
    public boolean matches(Message message) {
        return message.getPayloadType() != null && payloadType.isAssignableFrom(message.getPayloadType());
    }

    @Override
    public Class<?> supportedPayloadType() {
        return payloadType;
    }
}
