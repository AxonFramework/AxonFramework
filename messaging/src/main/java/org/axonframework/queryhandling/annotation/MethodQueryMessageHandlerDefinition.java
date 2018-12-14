/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.queryhandling.annotation;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Definition of handlers that can handle QueryMessages. These handlers are wrapped with a QueryHandlingMember that
 * exposes query-specific handler information.
 */
public class MethodQueryMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @SuppressWarnings("unchecked")
    @Override
    public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {
        return original.annotationAttributes(QueryHandler.class)
                       .map(attr -> (MessageHandlingMember<T>)
                               new MethodQueryMessageHandlerDefinition.MethodQueryMessageHandlingMember(original, attr))
                       .orElse(original);
    }

    private class MethodQueryMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> implements QueryHandlingMember<T> {

        private final String queryName;
        private final Type resultType;

        public MethodQueryMessageHandlingMember(MessageHandlingMember<T> original, Map<String, Object> attr) {
            super(original);

            String queryNameAttribute = (String) attr.get("queryName");
            if ("".equals(queryNameAttribute)) {
                queryNameAttribute = original.payloadType().getName();
            }
            queryName = queryNameAttribute;

            resultType = original.unwrap(Method.class)
                                 .map(this::queryResultType)
                                 .orElseThrow(() -> new UnsupportedHandlerException(
                                         "@QueryHandler annotation can only be put on methods.",
                                         original.unwrap(Member.class).orElse(null)
                                 ));
            if (Void.TYPE.equals(resultType)) {
                throw new UnsupportedHandlerException(
                        "@QueryHandler annotated methods must not declare void return type",
                        original.unwrap(Member.class).orElse(null)
                );
            }
        }

        private Type queryResultType(Method method) {
            if (Void.class.equals(method.getReturnType())) {
                throw new UnsupportedHandlerException(
                        "@QueryHandler annotated methods must not declare void return type", method
                );
            }
            return method.getGenericReturnType();
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean canHandle(Message<?> message) {
            return super.canHandle(message)
                    && message instanceof QueryMessage
                    && queryName.equals(((QueryMessage) message).getQueryName())
                    && ((QueryMessage) message).getResponseType().matches(resultType);
        }

        @Override
        public String getQueryName() {
            return queryName;
        }

        public Type getResultType() {
            return resultType;
        }
    }
}
