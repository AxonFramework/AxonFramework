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

package org.axonframework.config;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.invokeAndGetMethodValue;

/**
 * Utility class used to resolve {@link LifecycleHandler}s to be registered to the {@link Configuration}. A {@link
 * StartHandler} annotated lifecycle handler will be registered through {@link Configuration#onStart(int,
 * LifecycleHandler)}, whilst a {@link ShutdownHandler} annotated lifecycle handler will be registered through {@link
 * Configuration#onShutdown(int, LifecycleHandler)}.
 *
 * @author Steven van Beelen
 * @see ShutdownHandler
 * @see StartHandler
 * @see LifecycleHandler
 * @since 4.3
 */
public abstract class LifecycleHandlerInspector {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String LIFECYCLE_PHASE_ATTRIBUTE_NAME = "phase";

    private LifecycleHandlerInspector() {
        // Utility class
    }

    /**
     * Register any lifecycle handlers found on given {@code component} with given {@code configuration}.
     * <p>
     * If given {@code component} implements {@link Lifecycle}, will allow it to register lifecycle handlers with
     * the configuration. Otherwise, will resolve {@link StartHandler} and {@link ShutdownHandler} annotated lifecycle
     * handlers in the given {@code component}. If present, they will be registered on the given {@code configuration}
     * through the {@link Configuration#onStart(int, LifecycleHandler)} and
     * {@link Configuration#onShutdown(int, LifecycleHandler)} methods. If the given {@code component} is {@code null}
     * it will be ignored.
     *
     * @param configuration the {@link Configuration} to register resolved lifecycle handlers to
     * @param component     the object to resolve lifecycle handlers for
     */
    public static void registerLifecycleHandlers(Configuration configuration, Object component) {
        if (component == null) {
            logger.debug("Ignoring [null] component for inspection as it wont participate in the lifecycle");
            return;
        }
        if (component instanceof Lifecycle) {
            ((Lifecycle) component).registerLifecycleHandlers(new Lifecycle.LifecycleRegistry() {
                @Override
                public void onStart(int phase, @Nonnull Lifecycle.LifecycleHandler action) {
                    configuration.onStart(phase, action::run);
                }

                @Override
                public void onShutdown(int phase, @Nonnull Lifecycle.LifecycleHandler action) {
                    configuration.onShutdown(phase, action::run);
                }
            });
        } else {
            registerLifecycleHandlers(configuration, component, StartHandler.class, Configuration::onStart);
            registerLifecycleHandlers(configuration, component, ShutdownHandler.class, Configuration::onShutdown);
        }
    }

    private static void registerLifecycleHandlers(Configuration configuration,
                                                  Object component,
                                                  Class<? extends Annotation> lifecycleAnnotation,
                                                  LifecycleRegistration registrationMethod) {
        for (Method method : ReflectionUtils.methodsOf(component.getClass())) {
            AnnotationUtils.findAnnotationAttributes(method, lifecycleAnnotation)
                           .ifPresent(lifecycleAnnotationAttributes -> {
                               if (method.getParameterCount() > 0) {
                                   throw new AxonConfigurationException(format(
                                           "The @%s annotated method [%s] should not contain any parameters"
                                                   + " as none are allowed on lifecycle handlers",
                                           lifecycleAnnotation.getSimpleName(), method
                                   ));
                               }
                               int phase = (int) lifecycleAnnotationAttributes.get(LIFECYCLE_PHASE_ATTRIBUTE_NAME);
                               LifecycleHandler lifecycleHandler = () -> invokeAndReturn(
                                       component, method, lifecycleAnnotation.getSimpleName(), phase
                               );
                               registrationMethod.registerLifecycleHandler(configuration, phase, lifecycleHandler);

                               logger.debug(
                                       "Found and registered a {} with phase [{}] from component [{}]",
                                       lifecycleAnnotation.getSimpleName(), phase, component.getClass().getSimpleName()
                               );
                           });
        }
    }


    /**
     * Will invoke the given {@code lifecycleHandler}. If the result is a {@link CompletableFuture}, this will be
     * returned as is. Otherwise a {@link CompletableFuture#completedFuture(Object)} using {@code null} will be
     * returned. If invoking the given method fails, an {@link LifecycleHandlerInvocationException} is added to an
     * exceptionally completed {@code CompletableFuture}.
     */
    private static CompletableFuture<?> invokeAndReturn(Object lifecycleComponent,
                                                        Method lifecycleHandler,
                                                        String handlerType,
                                                        int phase) {
        try {
            logger.debug(
                    "Invoking {} from component [{}] in phase [{}]",
                    handlerType, lifecycleComponent.getClass().getSimpleName(), phase
            );

            Object result = invokeAndGetMethodValue(lifecycleHandler, lifecycleComponent);

            return result instanceof CompletableFuture
                   ? (CompletableFuture<?>) result
                   : CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            CompletableFuture<Void> exceptionallyCompletedFuture = new CompletableFuture<>();
            exceptionallyCompletedFuture.completeExceptionally(
                    new LifecycleHandlerInvocationException(lifecycleHandler, lifecycleComponent, e)
            );
            return exceptionallyCompletedFuture;
        }
    }

    /**
     * Functional interface towards the registration of a {@code lifecycleHandler} on the given {@code phase} to the
     * {@link Configuration}.
     */
    @FunctionalInterface
    private interface LifecycleRegistration {

        void registerLifecycleHandler(Configuration configuration, int phase, LifecycleHandler lifecycleHandler);
    }
}
