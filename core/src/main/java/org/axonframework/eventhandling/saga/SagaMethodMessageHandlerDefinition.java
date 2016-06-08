/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.saga;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.HandlerEnhancerDefinition;
import org.axonframework.common.annotation.MessageHandler;
import org.axonframework.common.property.Property;
import org.axonframework.common.property.PropertyAccessStrategy;

import java.lang.reflect.Executable;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;

/**
 * Utility class that inspects annotation on a Saga instance and returns the relevant configuration for its Event
 * Handlers.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class SagaMethodMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public <T> MessageHandler<T> wrapHandler(MessageHandler<T> original) {
        Optional<Map<String, Object>> annotationAttributes = original.annotationAttributes(SagaEventHandler.class);
        SagaCreationPolicy creationPolicy = original.annotationAttributes(StartSaga.class)
                .map(attr -> ((boolean)attr.getOrDefault("forceNew", false)) ? SagaCreationPolicy.ALWAYS : SagaCreationPolicy.IF_NONE_FOUND).orElse(SagaCreationPolicy.NONE);

        return annotationAttributes
                .map(attr -> doWrapHandler(original, creationPolicy, (String) attr.get("keyName"), (String) attr.get("associationProperty")))
                .orElse(original);
    }

    private <T> MessageHandler<T> doWrapHandler(MessageHandler<T> original, SagaCreationPolicy creationPolicy,
                                                String associationKeyName, String associationPropertyName) {
        String associationKey = associationKey(associationKeyName, associationPropertyName);
        Property<?> associationProperty = PropertyAccessStrategy.getProperty(original.payloadType(),
                                                                             associationPropertyName);
        boolean endingHandler = original.hasAnnotation(EndSaga.class);
        if (associationProperty == null) {
            String handler = original.unwrap(Executable.class).map(Executable::toGenericString).orElse("unknown");
            throw new AxonConfigurationException(format("SagaEventHandler %s defines a property %s that is not "
                                                                + "defined on the Event it declares to handle (%s)",
                                                        handler, associationPropertyName,
                                                        original.payloadType().getName()
            ));
        }

        return new SagaMethodMessageHandler<>(original, creationPolicy,
                                              associationKey, associationProperty, endingHandler);
    }

    private String associationKey(String keyName, String associationProperty) {
        return "".equals(keyName) ?  associationProperty : keyName;
    }

}
