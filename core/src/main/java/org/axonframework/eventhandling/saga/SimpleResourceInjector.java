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

package org.axonframework.eventhandling.saga;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.axonframework.common.ReflectionUtils.fieldsOf;
import static org.axonframework.common.ReflectionUtils.methodsOf;

/**
 * A resource injector that checks for {@see javax.inject.Inject} annotated fields and setter methods to inject
 *  resources.
 * If a field is annotated with {@see javax.inject.Inject}, a Resource of the type of that field is injected into it,
 *  if present.
 * If a method is annotated with {@see javax.inject.Inject}, the method is invoked with a Resource of the type of the
 *  first parameter, if present.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class SimpleResourceInjector implements ResourceInjector {

    private static final Logger logger = LoggerFactory.getLogger(SimpleResourceInjector.class);

    private static final String FULLY_QUALIFIED_CLASS_NAME_INJECT = "javax.inject.Inject";

    private final Iterable<?> resources;

    /**
     * Initializes the resource injector to inject to given <code>resources</code>.
     *
     * @param resources The resources to inject
     */
    public SimpleResourceInjector(Object... resources) {
        this(Arrays.asList(resources));
    }

    /**
     * Initializes the resource injector to inject to given <code>resources</code>.
     *
     * @param resources The resources to inject
     */
    public SimpleResourceInjector(Collection<?> resources) {
        this.resources = new ArrayList<>(resources);
    }

    @Override
    public void injectResources(Object saga) {
        injectFieldResources(saga);
        injectMethodResources(saga);
    }

    private void injectFieldResources(Object saga) {
        fieldsOf(saga.getClass()).forEach(field -> {
            Optional.ofNullable(AnnotationUtils.findAnnotation(field, FULLY_QUALIFIED_CLASS_NAME_INJECT))
                    .ifPresent(annotatedFields -> {
                        Class<?> requiredType = field.getType();
                        StreamSupport.stream(resources.spliterator(), false)
                                .filter(requiredType::isInstance)
                                .forEach(resource -> injectFieldResource(saga, field, resource));
                    });
        });
    }

    private void injectFieldResource(Object saga, Field injectField, Object resource) {
        try {
            ReflectionUtils.ensureAccessible(injectField);
            injectField.set(saga, resource);
        } catch (IllegalAccessException e) {
            logger.warn("Unable to inject resource. Exception while setting field: ", e);
        }
    }

    private void injectMethodResources(Object saga) {
        methodsOf(saga.getClass()).forEach(method -> {
            Optional.ofNullable(AnnotationUtils.findAnnotation(method, FULLY_QUALIFIED_CLASS_NAME_INJECT))
                    .ifPresent(annotatedMethods -> {
                        Class<?> requiredType = method.getParameterTypes()[0];
                        StreamSupport.stream(resources.spliterator(), false)
                                .filter(requiredType::isInstance)
                                .forEach(resource -> injectMethodResource(saga, method, resource));
                    });
        });
    }

    private void injectMethodResource(Object saga, Method injectMethod, Object resource) {
        try {
            ReflectionUtils.ensureAccessible(injectMethod);
            injectMethod.invoke(saga, resource);
        } catch (IllegalAccessException e) {
            logger.warn("Unable to inject resource. Exception while invoking setter: ", e);
        } catch (InvocationTargetException e) {
            logger.warn("Unable to inject resource. Exception while invoking setter: ", e.getCause());
        }
    }

}
