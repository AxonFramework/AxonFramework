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

package org.axonframework.spring.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Conditional(NoBeanOfType.NoBeanOfTypeDefined.class)
public @interface NoBeanOfType {

    Class<?> value();

    class NoBeanOfTypeDefined implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Map<String, Object> attributes = metadata.getAnnotationAttributes(NoBeanOfType.class.getName(), false);
            Class<?> clazz = (Class<?>) attributes.get("value");
            return context.getBeanFactory().getBeanNamesForType(clazz).length == 0;
        }
    }

}
