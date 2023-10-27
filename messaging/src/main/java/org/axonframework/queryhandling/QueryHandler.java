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
package org.axonframework.queryhandling;

import org.axonframework.messaging.annotation.MessageHandler;

import java.lang.annotation.*;

/**
 * Marker annotation to mark any method on an object as being a QueryHandler. Use the {@link
 * org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter AnnotationQueryHandlerAdapter} to subscribe
 * the annotated class to the query bus.
 * <p>
 * The annotated method's first parameter is the query handled by that method. Optionally, the query handler may
 * specify a second parameter of type {@link org.axonframework.messaging.MetaData}. The active MetaData will be
 * passed if that parameter is supplied.
 *
 * @author Marc Gathier
 * @since 3.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@MessageHandler(messageType = QueryMessage.class)
public @interface QueryHandler {

    /**
     * The name of the Query this handler listens to. Defaults to the fully qualified class name of the payload type
     * (i.e. first parameter).
     *
     * @return The query name
     */
    String queryName() default "";

}
