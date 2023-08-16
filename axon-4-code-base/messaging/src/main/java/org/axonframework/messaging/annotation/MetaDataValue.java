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

import java.lang.annotation.*;

/**
 * Annotation that indicates the parameter needs to be resolved to the value of the Message MetaData stored under the
 * given {@code key}. If {@code required}, and no such MetaData value is available, the handler will not be
 * invoked.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@Documented
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MetaDataValue {

    /**
     * The key of the MetaData field to inject as method parameter.
     */
    String value();

    /**
     * Indicates whether the MetaData must be available in order for the Message handler method to be invoked. Defaults
     * to {@code false}, in which case {@code null} is injected as parameter.
     * <p/>
     * Note that if the annotated parameter is a primitive type, the required property will always be
     * {@code true}.
     */
    boolean required() default false;
}
