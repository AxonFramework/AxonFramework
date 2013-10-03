/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.common.configuration;

import org.axonframework.commandhandling.annotation.CurrentUnitOfWorkParameterResolverFactory;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.DefaultParameterResolverFactory;
import org.axonframework.common.annotation.MultiParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.eventhandling.annotation.SequenceNumberParameterResolverFactory;
import org.axonframework.eventhandling.annotation.TimestampParameterResolverFactory;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Class providing access to the configuration of annotated classes, such as Aggregates and Sagas. Since these classes
 * are instantiated outside of Axon's control, they access this class to retrieve specific configuration.
 * <p/>
 *
 * @author Allard Buijze
 * @since 2.1
 */
public class AnnotationConfiguration implements AnnotationConfigurationBuilder, AnnotationConfigurationReader {

    private static final AnnotationConfiguration DEFAULT = new AnnotationConfiguration();
    private static final ConcurrentMap<Class<?>, AnnotationConfiguration> CONFIGS = new ConcurrentHashMap<Class<?>, AnnotationConfiguration>();

    private final MutableParameterResolverFactory parameterResolverFactory = new MutableParameterResolverFactory();
    private final Class<?> type;

    /**
     * Returns the default configuration. This configuration is used by all classes.
     *
     * @return the builder that allows modification of the default configuration
     */
    public static AnnotationConfigurationBuilder defaultConfiguration() {
        return DEFAULT;
    }

    /**
     * Accesses the configuration for the given <code>type</code> for reading purposes. If no explicit configuration
     * for the given <code>type</code> exists, that of the super type is looked up. Ultimately, the default
     * configuration is returned.
     * <p/>
     * Note that only parent classes are investigated. Configuration provided for interfaces is disregarded.
     *
     * @param type The type for which to look up the configuration.
     * @return the configuration for the given <code>type</code>.
     */
    public static AnnotationConfigurationBuilder configure(Class<?> type) {
        if (!CONFIGS.containsKey(type)) {
            CONFIGS.putIfAbsent(type, new AnnotationConfiguration(type));
        }
        return CONFIGS.get(type);
    }

    /**
     * Accesses the configuration for the given <code>type</code> for reading purposes. If no explicit configuration
     * for the given <code>type</code> exists, that of the super type is looked up. Ultimately, the default
     * configuration is returned.
     * <p/>
     * Note that only parent classes are investigated. Configuration provided for interfaces is disregarded.
     *
     * @param type The type for which to look up the configuration.
     * @return the configuration for the given <code>type</code>.
     */
    public static AnnotationConfigurationReader readFor(Class<?> type) {
        Class<?> typeLevel = type;
        AnnotationConfiguration config;
        while ((config = CONFIGS.get(typeLevel)) == null) {
            if (typeLevel.equals(Object.class)) {
                return DEFAULT;
            }
            typeLevel = typeLevel.getSuperclass();
        }
        return config;
    }

    private AnnotationConfiguration() {
        this.type = null;
        parameterResolverFactory.addDefault(new DefaultParameterResolverFactory());
        parameterResolverFactory.addDefault(new SequenceNumberParameterResolverFactory());
        parameterResolverFactory.addDefault(new CurrentUnitOfWorkParameterResolverFactory());
        parameterResolverFactory.addDefault(new TimestampParameterResolverFactory());
        parameterResolverFactory.addDefault(ClasspathParameterResolverFactory.forClass(AnnotationConfiguration.class));
    }

    private AnnotationConfiguration(Class<?> type) {
        this.type = type;
        parameterResolverFactory.addDefault(ClasspathParameterResolverFactory.forClass(type));
    }

    @Override
    public AnnotationConfigurationBuilder useParameterResolverFactory(ParameterResolverFactory additionalFactory) {
        parameterResolverFactory.add(additionalFactory);
        return this;
    }

    @Override
    public ParameterResolverFactory getParameterResolverFactory() {
        if (type == null) {
            return parameterResolverFactory;
        } else if (Object.class.equals(type)) {
            return new MultiParameterResolverFactory(parameterResolverFactory, DEFAULT.getParameterResolverFactory());
        }
        return new MultiParameterResolverFactory(parameterResolverFactory,
                                                 readFor(type.getSuperclass()).getParameterResolverFactory());
    }

    @Override
    public AnnotationConfigurationBuilder reset() {
        parameterResolverFactory.clear();
        return this;
    }

    /**
     * Resets the configuration for the given <code>type</code>. This removes all references to this configuration,
     * allowing it to be garbage collected.
     * <p/>
     * Subsequent calls to {@link #readFor(Class)} will return the configuration of one of the parent types or the
     * default configuration.
     *
     * @param type the type for which to reset the configuration
     */
    public static void reset(Class<?> type) {
        CONFIGS.remove(type);
    }

    /**
     * Resets all configurations, and clears any customizations made to the default configuration.
     */
    public static void resetAll() {
        CONFIGS.clear();
        DEFAULT.reset();
    }

    private static class MutableParameterResolverFactory implements ParameterResolverFactory {

        private final Set<ParameterResolverFactory> factories = new CopyOnWriteArraySet<ParameterResolverFactory>();
        private final Set<ParameterResolverFactory> defaultFactories = new CopyOnWriteArraySet<ParameterResolverFactory>();

        @Override
        public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                                Annotation[] parameterAnnotations) {
            for (ParameterResolverFactory factory : defaultFactories) {
                ParameterResolver resolver = factory.createInstance(memberAnnotations, parameterType,
                                                                    parameterAnnotations);
                if (resolver != null) {
                    return resolver;
                }
            }
            for (ParameterResolverFactory factory : factories) {
                ParameterResolver resolver = factory.createInstance(memberAnnotations, parameterType,
                                                                    parameterAnnotations);
                if (resolver != null) {
                    return resolver;
                }
            }
            return null;
        }

        public void add(ParameterResolverFactory parameterResolverFactory) {
            this.factories.add(parameterResolverFactory);
        }

        public void addDefault(ParameterResolverFactory parameterResolverFactory) {
            this.defaultFactories.add(parameterResolverFactory);
        }

        public void clear() {
            this.factories.clear();
        }
    }
}
