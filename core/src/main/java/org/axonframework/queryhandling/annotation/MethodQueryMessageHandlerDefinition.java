/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.queryhandling.annotation;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.Map;
import java.util.Spliterator;
import java.util.stream.Stream;

/**
 * Definition of handlers that can handle QueryMessages. These handlers are wrapped with a QueryHandlingMember that
 * exposes query-specific handler information.
 */
public class MethodQueryMessageHandlerDefinition implements HandlerEnhancerDefinition {

    private static final Class[] SUPPORTED_COLLECTION_TYPES = new Class[]{Collection.class, Iterable.class, Spliterator.class, Stream.class};

    @SuppressWarnings("unchecked")
    @Override
    public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {
        return original.annotationAttributes(QueryHandler.class)
                .map(attr -> (MessageHandlingMember<T>) new MethodQueryMessageHandlerDefinition.MethodQueryMessageHandlingMember(original, attr))
                .orElse(original);
    }

    private class MethodQueryMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> implements QueryHandlingMember<T> {

        private final String queryName;
        private final Class<?> resultType;

        public MethodQueryMessageHandlingMember(MessageHandlingMember<T> original, Map<String, Object> attr) {
            super(original);
            String qn = (String) attr.get("queryName");
            if ("".equals(qn)) {
                qn = original.payloadType().getName();
            }
            queryName = qn;
            Class<?> declaredResponseType = (Class<?>) attr.get("responseType");
            if (Void.class.equals(declaredResponseType)) {
                resultType = original.unwrap(Method.class).map(this::queryResultType)
                                     .orElseThrow(() -> new UnsupportedHandlerException("@QueryHandler annotation can only be put on methods.",
                                                                                        original.unwrap(Member.class).orElse(null)));
            } else {
                resultType = (Class<?>) attr.get("responseType");
            }
            if (Void.TYPE.equals(resultType)) {
                throw new UnsupportedHandlerException("@QueryHandler annotated methods must not declare void return type",
                                                      original.unwrap(Member.class).orElse(null));
            }
        }

        private Class<?> queryResultType(Method method) {
            if (method.getReturnType().isArray()) {
                return method.getReturnType().getComponentType();
            } else if (isCollectionLike(method.getReturnType())) {
                Type rType = method.getGenericReturnType();
                if (rType instanceof ParameterizedType) {
                    Type[] args = ((ParameterizedType) rType).getActualTypeArguments();
                    if (args.length > 0 && args[0] instanceof Class<?>) {
                        return (Class<?>) args[0];
                    } else if (args.length > 0 && args[0] instanceof WildcardType) {
                        Type[] upperBounds = ((WildcardType) args[0]).getUpperBounds();
                        if (upperBounds.length == 1 && upperBounds[0] instanceof Class<?>) {
                            return (Class<?>) upperBounds[0];
                        }
                    }
                }
                throw new AxonConfigurationException("Cannot extract query result type from declared return value of method: " + method.toGenericString());
            }
            return method.getReturnType();
        }

        @SuppressWarnings("unchecked")
        private boolean isCollectionLike(Class<?> type) {
            for (Class collectionType : SUPPORTED_COLLECTION_TYPES) {
                if (collectionType.isAssignableFrom(type)) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean canHandle(Message<?> message) {
            return super.canHandle(message)
                    && message instanceof QueryMessage
                    && queryName.equals(((QueryMessage) message).getQueryName())
                    && ((QueryMessage) message).getResponseType().isAssignableFrom(resultType);
        }

        @Override
        public String getQueryName() {
            return queryName;
        }

        public Class<?> getResultType() {
            return resultType;
        }
    }
}
