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

package org.axonframework.common.annotation;

import org.axonframework.messaging.Message;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

public interface MessageHandler<T> {

    Class<?> payloadType();

    int priority();

    boolean canHandle(Message<?> message);

    Object handle(Message<?> message, T target) throws Exception;

    <HT> Optional<HT> unwrap(Class<HT> handlerType);

    Optional<Map<String, Object>> annotationAttributes(Class<? extends Annotation> annotationType);

    boolean hasAnnotation(Class<? extends Annotation> annotationType);
}
