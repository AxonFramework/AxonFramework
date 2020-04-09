/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.messaging.interceptors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-Annotation used to demarcate {@link MessageHandlerInterceptor} annotated methods as interceptors that should
 * only act on the result of a handler invocation. This gives these handlers the opportunity to act on the result only,
 * without intercepting the call on the way <em>to</em> the handler.
 * <p>
 * The {@link #resultType()} can be used to limit the types of responses the handler should be invoked for.
 * <p>
 * This annotation is exclusively meant as a Meta-Annotation and cannot not be placed directly on a method.
 * @author Allard Buijze
 * @since 4.4
 * @see ExceptionHandler
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE})
public @interface ResultHandler {

    /**
     * The type of result object that the annotated handler should be invoked for. The handler will be ignored if the
     * actual response type (regular or thrown exception) is not an instance of the type defined by this property, even
     * when the parameters of the method match the result.
     */
    Class<?> resultType() default Object.class;
}
