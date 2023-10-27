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

package org.axonframework.messaging.annotation;

import org.axonframework.messaging.Message;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation indicating that a member method should be able to respond to {@link Message}s.
 * <p>
 * It is not recommended to put this annotation on methods or constructors directly. Instead, put this annotation on
 * another annotation that expresses the type of message handled.
 *
 * @author Allard Buijze
 * @since 3.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
@HasHandlerAttributes
public @interface MessageHandler {

    /**
     * Specifies the type of message that can be handled by the member method. Defaults to any {@link Message}.
     */
    Class<? extends Message> messageType() default Message.class;

    /**
     * Specifies the type of message payload that can be handled by the member method. The payload of the message should
     * be assignable to this type. Defaults to any {@link Object}.
     */
    Class<?> payloadType() default Object.class;
}
