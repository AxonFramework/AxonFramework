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

package org.axonframework.spring.config;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/**
 * Annotation to be used in a configuration class on @{@link org.springframework.context.annotation.Bean} annotated
 * methods if the method should only provide its bean if a bean of a given type does not exist yet.
 *
 * @author Allard Buijze
 * @since 3.0
 * @deprecated Use Spring Boot autoconfiguration or register the individual beans explicitly. Check the "See also" list
 * for which individual beans to register.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Conditional(NoBeanOfType.NoBeanOfTypeDefined.class)
@Deprecated
public @interface NoBeanOfType {

    /**
     *  The class of the bean to find in the Spring context. If a bean with that type already exists the annotated
     *  method will not also provide its bean.
     */
    Class<?> value();

    /**
     * Condition that checks if a bean with given class already exists in the Spring context. If so this condition
     * returns {@code false} preventing the creation of another bean.
     */
    class NoBeanOfTypeDefined implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Map<String, Object> attributes = metadata.getAnnotationAttributes(NoBeanOfType.class.getName(), false);
            Class<?> clazz = (Class<?>) attributes.get("value");
            return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context.getBeanFactory(), clazz).length == 0;
        }
    }
}
