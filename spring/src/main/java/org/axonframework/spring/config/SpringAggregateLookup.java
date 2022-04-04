/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.modelling.command.Repository;
import org.axonframework.spring.eventsourcing.SpringPrototypeAggregateFactory;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

import static java.lang.String.format;

/**
 * A {@link BeanDefinitionRegistryPostProcessor} implementation that scans for Aggregate types and registers a {@link
 * SpringAggregateConfigurer configurer} for each Aggregate found.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
public class SpringAggregateLookup implements BeanDefinitionRegistryPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SpringAggregateLookup.class);

    /**
     * Builds a hierarchy model from the given {@code aggregatePrototypes} found in the given {@code beanFactory}.
     *
     * @param beanFactory         The beanFactory containing the definitions of the Aggregates.
     * @param aggregatePrototypes The prototype beans found for each individual type of Aggregate.
     * @param <A>                 The type of Aggregate.
     * @return A hierarchy model with subtypes for each declared main Aggregate.
     */
    @SuppressWarnings("unchecked")
    public static <A> Map<SpringAggregate<? super A>, Map<Class<? extends A>, String>> buildAggregateHierarchy(
            ListableBeanFactory beanFactory,
            String[] aggregatePrototypes
    ) {
        Map<SpringAggregate<? super A>, Map<Class<? extends A>, String>> hierarchy = new HashMap<>();
        for (String prototype : aggregatePrototypes) {
            Class<A> aggregateType = (Class<A>) beanFactory.getType(prototype);
            SpringAggregate<A> springAggregate = new SpringAggregate<>(prototype, aggregateType);
            Class<? super A> topType = topAnnotatedAggregateType(aggregateType);
            SpringAggregate<? super A> topSpringAggregate =
                    new SpringAggregate<>(beanName(beanFactory, topType), topType);
            hierarchy.compute(topSpringAggregate, (type, subtypes) -> {
                if (subtypes == null) {
                    subtypes = new HashMap<>();
                }
                if (!type.equals(springAggregate)) {
                    subtypes.put(aggregateType, prototype);
                }
                return subtypes;
            });
        }
        return hierarchy;
    }

    private static <A> String beanName(ListableBeanFactory beanFactory, Class<A> type) {
        String[] beanNamesForType = beanFactory.getBeanNamesForType(type);
        if (beanNamesForType.length == 0) {
            throw new AxonConfigurationException(format("There are no spring beans for '%s' defined.", type.getName()));
        } else {
            if (beanNamesForType.length != 1) {
                logger.warn("There are {} beans defined for '{}'.", beanNamesForType.length, type.getName());
            }
            return beanNamesForType[0];
        }
    }

    private static <A> Class<? super A> topAnnotatedAggregateType(Class<A> type) {
        Class<? super A> top = type;
        Class<? super A> topAnnotated = top;
        while (!Object.class.equals(top) && !Object.class.equals(top.getSuperclass())) {
            top = top.getSuperclass();
            if (top.isAnnotationPresent(Aggregate.class)) {
                topAnnotated = top;
            }
        }
        return topAnnotated;
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry)) {
            logger.warn("Given bean factory is not a BeanDefinitionRegistry. Cannot auto-configure Aggregates");
            return;
        }

        BeanDefinitionRegistry bdRegistry = (BeanDefinitionRegistry) beanFactory;
        String[] aggregateBeans = beanFactory.getBeanNamesForAnnotation(Aggregate.class);

        Map<SpringAggregate<? super Object>, Map<Class<?>, String>> hierarchy =
                buildAggregateHierarchy(beanFactory, aggregateBeans);

        //noinspection TypeParameterExplicitlyExtendsObject
        for (Map.Entry<SpringAggregate<? super Object>, Map<Class<? extends Object>, String>> aggregate : hierarchy.entrySet()) {
            Class<?> aggregateType = aggregate.getKey().getClassType();
            String aggregatePrototype = aggregate.getKey().getBeanName();
            //noinspection TypeParameterExplicitlyExtendsObject
            Set<Class<? extends Object>> subTypes = aggregate.getValue().keySet();

            String registrarBeanName = aggregatePrototype + "$$Registrar";
            if (beanFactory.containsBeanDefinition(registrarBeanName)) {
                logger.info("Registrar for {} already available. Skipping configuration", aggregatePrototype);
                break;
            }
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(aggregatePrototype);
            if (beanDefinition.isPrototype() && aggregateType != null) {
                AnnotationUtils.findAnnotationAttributes(aggregateType, Aggregate.class)
                               .map(props -> buildAggregateBeanDefinition(
                                       beanFactory, aggregateType, aggregatePrototype, subTypes, props
                               ))
                               .ifPresent(registrarBeanDefinition -> bdRegistry.registerBeanDefinition(
                                       registrarBeanName, registrarBeanDefinition
                               ));
            }
        }
    }

    private BeanDefinition buildAggregateBeanDefinition(ConfigurableListableBeanFactory registry,
                                                        Class<?> aggregateType,
                                                        String aggregateBeanName,
                                                        Set<Class<?>> subTypes,
                                                        Map<String, Object> props) {
        BeanDefinitionBuilder beanDefinitionBuilder =
                BeanDefinitionBuilder.genericBeanDefinition(SpringAggregateConfigurer.class)
                                     .setRole(BeanDefinition.ROLE_APPLICATION)
                                     .addConstructorArgValue(aggregateType)
                                     .addConstructorArgValue(subTypes);
        if (!"".equals(props.get("snapshotFilter"))) {
            beanDefinitionBuilder.addPropertyValue("snapshotFilter", props.get("snapshotFilter"));
        }
        if (!"".equals(props.get("snapshotTriggerDefinition"))) {
            beanDefinitionBuilder.addPropertyValue("snapshotTriggerDefinition", props.get("snapshotTriggerDefinition"));
        }
        if (!"".equals(props.get("commandTargetResolver"))) {
            beanDefinitionBuilder.addPropertyValue("commandTargetResolver", props.get("commandTargetResolver"));
        }
        if (!"".equals(props.get("repository"))) {
            beanDefinitionBuilder.addPropertyValue("repository", props.get("repository"));
        } else if (registry.containsBean(aggregateBeanName + "Repository")) {
            Class<?> type = registry.getType(aggregateBeanName + "Repository");
            if (type == null || Repository.class.isAssignableFrom(type)) {
                beanDefinitionBuilder.addPropertyReference("repository", aggregateBeanName + "Repository");
            }
        }
        String aggregateFactory = aggregateBeanName + "AggregateFactory";
        if (!registry.containsBeanDefinition(aggregateFactory)) {
            ((BeanDefinitionRegistry) registry).registerBeanDefinition(
                    aggregateFactory,
                    BeanDefinitionBuilder.genericBeanDefinition(SpringPrototypeAggregateFactory.class)
                                         .addConstructorArgValue(aggregateBeanName)
                                         .getBeanDefinition()
            );
        }
        beanDefinitionBuilder.addPropertyValue("aggregateFactory", aggregateFactory);
        return beanDefinitionBuilder.getBeanDefinition();
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        // No action required.
    }

    static class SpringAggregate<T> {

        private final String beanName;
        private final Class<T> classType;

        private SpringAggregate(String beanName, Class<T> classType) {
            this.beanName = beanName;
            this.classType = classType;
        }

        public String getBeanName() {
            return beanName;
        }

        public Class<T> getClassType() {
            return classType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SpringAggregate<?> that = (SpringAggregate<?>) o;
            return Objects.equals(beanName, that.beanName) &&
                    Objects.equals(classType, that.classType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(beanName, classType);
        }
    }
}
