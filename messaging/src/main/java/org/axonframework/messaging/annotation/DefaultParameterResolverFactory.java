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

package org.axonframework.messaging.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Priority;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for the default parameter resolvers. This factory is capable for providing parameter resolvers for Message,
 * MetaData and @MetaDataValue annotated parameters.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@Priority(Priority.LOW)
public class DefaultParameterResolverFactory implements ParameterResolverFactory {

    @Nullable
    @Override
    public ParameterResolver createInstance(@Nonnull Executable executable, @Nonnull Parameter[] parameters,
                                            int parameterIndex) {

        Class<?> parameterType = parameters[parameterIndex].getType();
        if (Message.class.isAssignableFrom(parameterType)) {
            return new MessageParameterResolver(parameterType);
        }
        Optional<Map<String, Object>>
                metaDataValueAnnotation = AnnotationUtils.findAnnotationAttributes(parameters[parameterIndex], MetaDataValue.class);
        if (metaDataValueAnnotation.isPresent()) {
            return new AnnotatedMetaDataParameterResolver(metaDataValueAnnotation.get(), parameterType);
        }
        if (org.axonframework.messaging.MetaData.class.isAssignableFrom(parameterType)) {
            return MetaDataParameterResolver.INSTANCE;
        }
        if (parameterIndex == 0) {
            Class<?> payloadType = (Class<?>) AnnotationUtils.findAnnotationAttributes(executable, MessageHandler.class)
                                                             .map(attr -> attr.get("payloadType"))
                                                             .orElse(Object.class);
            if (payloadType.isAssignableFrom(parameterType)) {
                return new PayloadParameterResolver(parameterType);
            }
        }
        return null;
    }

    private static class AnnotatedMetaDataParameterResolver implements ParameterResolver<Object> {

        private static final String REQUIRED_PROPERTY = "required";
        private static final String META_DATA_VALUE_PROPERTY = "metaDataValue";

        private final Map<String, Object> metaDataValue;
        private final Class<?> parameterType;

        public AnnotatedMetaDataParameterResolver(Map<String, Object> metaDataValue, Class<?> parameterType) {
            this.metaDataValue = metaDataValue;
            this.parameterType = parameterType;
        }

        @Override
        public Object resolveParameterValue(@Nonnull ProcessingContext context) {
            return Message.fromContext(context)
                          .getMetaData()
                          .get(metaDataValue.get(META_DATA_VALUE_PROPERTY).toString());
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            Message<?> message = Message.fromContext(context);
            if (message == null) {
                return false;
            }
            return !(parameterType.isPrimitive() || (boolean) metaDataValue.get(REQUIRED_PROPERTY))
                    || (
                    message.getMetaData().containsKey(metaDataValue.get(META_DATA_VALUE_PROPERTY).toString())
                            && parameterType.isInstance(message.getMetaData().get(metaDataValue.get(META_DATA_VALUE_PROPERTY).toString()))
            );
        }
    }

    private static final class MetaDataParameterResolver implements ParameterResolver<MetaData> {

        private static final MetaDataParameterResolver INSTANCE = new MetaDataParameterResolver();

        private MetaDataParameterResolver() {
        }

        @Nullable
        @Override
        public MetaData resolveParameterValue(@Nonnull ProcessingContext context) {
            Message<?> message = Message.fromContext(context);
            return message.getMetaData();
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return Message.fromContext(context) != null;
        }
    }

    private static class MessageParameterResolver implements ParameterResolver<Message<?>> {

        private final Class<?> parameterType;

        public MessageParameterResolver(Class<?> parameterType) {
            this.parameterType = parameterType;
        }

        @Nullable
        @Override
        public Message<?> resolveParameterValue(@Nonnull ProcessingContext context) {
            return Message.fromContext(context);
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            Message<?> message = Message.fromContext(context);
            if (message == null) {
                return false;
            }
            return parameterType.isAssignableFrom(message.getClass());
        }
    }
}
