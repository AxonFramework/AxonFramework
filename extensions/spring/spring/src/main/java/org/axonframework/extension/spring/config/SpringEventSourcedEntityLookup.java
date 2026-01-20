/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.spring.config;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.extension.spring.stereotype.EventSourced;
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

import static java.lang.String.format;
import static org.axonframework.common.StringUtils.lowerCaseFirstCharacterOf;

/**
 * A {@link BeanDefinitionRegistryPostProcessor} implementation that scans for Aggregate types and registers a
 * {@link SpringEventSourcedEntityConfigurer configurer} for each Aggregate found.
 *
 * @author Allard Buijze
 * @author Simon Zambrovski
 * @since 4.6.0
 */
@Internal
public class SpringEventSourcedEntityLookup implements BeanDefinitionRegistryPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SpringEventSourcedEntityLookup.class);

    private static final String ID_TYPE_CLASS = "idType";

    /**
     * Builds a hierarchy model from the given {@code entityPrototypes} found in the given {@code beanFactory}.
     *
     * @param beanFactory      The beanFactory containing the definitions of the entities.
     * @param entityPrototypes The prototype beans found for each individual type of entities.
     * @param <E>              The type of entity.
     * @return A hierarchy model with subtypes for each declared main entities.
     */
    @SuppressWarnings("unchecked")
    static <E> Map<SpringEntity<? super E>, Map<Class<? extends E>, String>> buildEntityHierarchy(
            ListableBeanFactory beanFactory,
            String[] entityPrototypes
    ) {
        Map<SpringEntity<? super E>, Map<Class<? extends E>, String>> hierarchy = new HashMap<>();
        for (String prototype : entityPrototypes) {
            Class<E> entityType = (Class<E>) beanFactory.getType(prototype);
            if (entityType == null) {
                logger.info("Cannot find Entity class for type [{}], hence ignoring.", prototype);
                break;
            }

            SpringEntity<E> springEntity = new SpringEntity<>(prototype, entityType);
            Class<? super E> topType = topAnnotatedEntityType(entityType);
            SpringEntity<? super E> topSpringEntity =
                    new SpringEntity<>(beanName(beanFactory, topType), topType);
            hierarchy.compute(topSpringEntity, (type, subtypes) -> {
                if (subtypes == null) {
                    subtypes = new HashMap<>();
                }
                if (!type.equals(springEntity)) {
                    subtypes.put(entityType, prototype);
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
        } else if (beanNamesForType.length != 1) {
            logger.debug(
                    "There are {} beans defined for type [{}], making this a polymorphic entities. "
                            + "There is a high likelihood the root is an abstract class, so no bean name is found. "
                            + "Hence we default to simple name of the root type.",
                    beanNamesForType.length, type.getName()
            );
            return lowerCaseFirstCharacterOf(type.getSimpleName());
        } else {
            return beanNamesForType[0];
        }
    }

    private static <E> Class<? super E> topAnnotatedEntityType(Class<E> type) {
        Class<? super E> top = type;
        Class<? super E> topAnnotated = top;
        while (!Object.class.equals(top) && !Object.class.equals(top.getSuperclass())) {
            top = top.getSuperclass();
            if (top.isAnnotationPresent(EventSourced.class)) {
                topAnnotated = top;
            }
        }
        return topAnnotated;
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry bdRegistry)) {
            logger.warn("Given bean factory is not a BeanDefinitionRegistry. Cannot auto-configure Entities");
            return;
        }

        String[] entitiesBeans = beanFactory.getBeanNamesForAnnotation(EventSourced.class);

        Map<SpringEntity<? super Object>, Map<Class<?>, String>> hierarchy =
                buildEntityHierarchy(beanFactory, entitiesBeans);

        //noinspection TypeParameterExplicitlyExtendsObject
        for (Map.Entry<SpringEntity<? super Object>, Map<Class<? extends Object>, String>> entity : hierarchy.entrySet()) {
            Class<?> entityType = entity.getKey().getClassType();
            Map<Class<?>, String> entitySubtypes = entity.getValue();
            String entityPrototype = entity.getKey().getBeanName();

            String registrarBeanName = entityPrototype + "$$Registrar";
            if (beanFactory.containsBeanDefinition(registrarBeanName)) {
                logger.info("Registrar for {} already available. Skipping configuration", entityPrototype);
                break;
            }

            if (hasSubtypesOrIsPrototype(entitySubtypes, beanFactory, entityPrototype) && entityType != null) {
                AnnotationUtils.findAnnotationAttributes(entityType, EventSourced.class)
                               .map(props -> buildEntityBeanDefinition(
                                       entityType,
                                       (Class<?>) Objects.requireNonNull(props.get(ID_TYPE_CLASS),
                                                                         "Id type must be provided for "
                                                                                 + entityPrototype)
                               ))
                               .ifPresent(registrarBeanDefinition -> bdRegistry.registerBeanDefinition(
                                       registrarBeanName, registrarBeanDefinition
                               ));
            }
        }
    }

    /**
     * When there are subtypes present we are dealing with a polymorphic entity. In those cases the root class should be
     * {@code abstract}, causing Spring to <b>not</b> add the {@link EventSourced} class to the
     * {@link org.springframework.context.ApplicationContext}. Due to this, there is no {@link BeanDefinition} present
     * to begin with. As such, we skip the {@link BeanDefinition#isPrototype()} check whenever there are subtypes. If
     * there are <b>no</b> subtypes, the {@code entityPrototype} is expected to reference a prototype
     * {@code BeanDefinition}.
     *
     * @param entitySubtypes  The collection of subtypes found for the given {@code entityPrototype}. May be empty.
     * @param beanFactory     The bean factory to retrieve the prototype {@link BeanDefinition} from, based on the given
     *                        {@code entityPrototype}.
     * @param entityPrototype The bean name of the {@link BeanDefinition}, expected to be a
     *                        {@link BeanDefinition#isPrototype() prototype bean}.
     * @return Whether there are subtypes present or whether the {@code entityPrototype} is a prototype
     * {@link BeanDefinition}.
     */
    private static boolean hasSubtypesOrIsPrototype(Map<Class<?>, String> entitySubtypes,
                                                    ConfigurableListableBeanFactory beanFactory,
                                                    String entityPrototype) {
        return !entitySubtypes.isEmpty() || beanFactory.getBeanDefinition(entityPrototype).isPrototype();
    }

    private BeanDefinition buildEntityBeanDefinition(
            Class<?> entityType,
            Class<?> idType) {
        BeanDefinitionBuilder beanDefinitionBuilder =
                BeanDefinitionBuilder.genericBeanDefinition(SpringEventSourcedEntityConfigurer.class)
                                     .setRole(BeanDefinition.ROLE_APPLICATION)
                                     .addConstructorArgValue(entityType)
                                     .addConstructorArgValue(idType);

        return beanDefinitionBuilder.getBeanDefinition();
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        // No action required.
    }

    /**
     * Spring entity bean wrapper.
     *
     * @param <T> type of the underlying entity class.
     */
    static class SpringEntity<T> {

        private final String beanName;
        private final Class<T> classType;

        private SpringEntity(String beanName, Class<T> classType) {
            this.beanName = beanName;
            this.classType = classType;
        }

        /**
         * Retrieves the bean name.
         *
         * @return The name of the entity bean.
         */
        public String getBeanName() {
            return beanName;
        }

        /**
         * Retrieves the entity type.
         *
         * @return The class of the entity.
         */
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
            SpringEntity<?> that = (SpringEntity<?>) o;
            return Objects.equals(beanName, that.beanName) &&
                    Objects.equals(classType, that.classType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(beanName, classType);
        }
    }
}
