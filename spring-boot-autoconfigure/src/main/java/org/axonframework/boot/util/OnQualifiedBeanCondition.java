/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.boot.util;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;

import java.util.stream.Stream;

import static org.axonframework.spring.SpringUtils.isQualifierMatch;

/**
 * {@link Condition} implementation to check for a bean instance of a specific class *and* a specific qualifier on it.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class OnQualifiedBeanCondition extends SpringBootCondition implements ConfigurationCondition {

    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ConfigurableListableBeanFactory bf = context.getBeanFactory();

        MultiValueMap<String, Object> annotationAttributes =
                metadata.getAllAnnotationAttributes(ConditionalOnQualifiedBean.class.getName(), true);
        Class conditionalClass = (Class) annotationAttributes.get("beanClass").get(0);
        String conditionalQualifier = (String) annotationAttributes.get("qualifier").get(0);


        return Stream.of(bf.getBeanNamesForType(conditionalClass))
                     .anyMatch(beanName -> isQualifierMatch(beanName, bf, conditionalQualifier))
                ? new ConditionOutcome(true, String.format(
                "Match found for class [%s] and qualifier [%s]", conditionalClass, conditionalQualifier))
                : new ConditionOutcome(false, String.format(
                "No match found for class [%s] and qualifier [%s]", conditionalClass, conditionalQualifier));
    }
}
