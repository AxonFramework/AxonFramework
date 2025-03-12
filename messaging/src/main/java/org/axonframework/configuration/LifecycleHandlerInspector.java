/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.FutureUtils;
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

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.axonframework.common.ReflectionUtils.invokeAndGetMethodValue;

/**
 * Utility class used to resolve {@link LifecycleHandler}s to be registered to the {@link NewConfiguration}.
 * <p>
 * A {@link StartHandler} annotated lifecycle handler will be registered through
 * {@link LifecycleRegistry#onStart(int, LifecycleHandler)}, whilst a {@link ShutdownHandler} annotated lifecycle
 * handler will be registered through {@link LifecycleRegistry#onShutdown(int, LifecycleHandler)}.
 *
 * @author Steven van Beelen
 * @see ShutdownHandler
 * @see StartHandler
 * @see LifecycleHandler
 * @since 4.3.0
 */
public abstract class LifecycleHandlerInspector {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String LIFECYCLE_PHASE_ATTRIBUTE_NAME = "phase";

    /**
     * Register any lifecycle handlers found on given {@code component} with given {@code config}.
     * <p>
     * If given {@code component} implements {@link Lifecycle}, will allow it to register lifecycle handlers with the
     * config. Otherwise, will resolve {@link StartHandler} and {@link ShutdownHandler} annotated lifecycle handlers in
     * the given {@code component}. If present, they will be registered on the given {@code config} through the
     * {@link LifecycleRegistry#onStart(int, LifecycleHandler)} and
     * {@link LifecycleRegistry#onShutdown(int, LifecycleHandler)} methods. If the given {@code component} is
     * {@code null} it will be ignored.
     *
     * @param lifecycleRegistry the {@link NewConfiguration} to register resolved lifecycle handlers to
     * @param component         the object to resolve lifecycle handlers for
     */
    public static void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycleRegistry,
                                                 @Nonnull Object component) {
        requireNonNull(lifecycleRegistry, "Cannot register lifecycle handlers on a null lifecycle operator.");
        requireNonNull(component, "Cannot register lifecycle handlers from a null component.");

        if (component instanceof Lifecycle lifecycleAwareComponent) {
            lifecycleAwareComponent.registerLifecycleHandlers(lifecycleRegistry);
        } else {
            registerLifecycleHandlers(lifecycleRegistry, component, StartHandler.class,
                                      LifecycleRegistry::onStart);
            registerLifecycleHandlers(lifecycleRegistry, component, ShutdownHandler.class,
                                      LifecycleRegistry::onShutdown);
        }
    }

    private static void registerLifecycleHandlers(LifecycleRegistry config,
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
                               registrationMethod.registerLifecycleHandler(config, phase, lifecycleHandler);

                               logger.debug(
                                       "Found and registered a {} with phase [{}] from component [{}]",
                                       lifecycleAnnotation.getSimpleName(), phase, component.getClass().getSimpleName()
                               );
                           });
        }
    }


    /**
     * Will invoke the given {@code lifecycleHandler}. If the result is a {@link CompletableFuture}, this will be
     * returned as is.
     * <p>
     * Otherwise, a {@link CompletableFuture#completedFuture(Object)} using {@code null} will be returned. If invoking
     * the given method fails, an {@link LifecycleHandlerInvocationException} is added to an exceptionally completed
     * {@code CompletableFuture}.
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
                    : FutureUtils.emptyCompletedFuture();
        } catch (Exception e) {
            CompletableFuture<Void> exceptionallyCompletedFuture = new CompletableFuture<>();
            exceptionallyCompletedFuture.completeExceptionally(
                    new LifecycleHandlerInvocationException(lifecycleHandler, lifecycleComponent, e)
            );
            return exceptionallyCompletedFuture;
        }
    }

    /**
     * Functional interface towards the registration of a {@code lifecycleHandler} in the given {@code phase} in the
     * {@link LifecycleRegistry}.
     */
    @FunctionalInterface
    private interface LifecycleRegistration {

        void registerLifecycleHandler(LifecycleRegistry config, int phase, LifecycleHandler handler);
    }


    private LifecycleHandlerInspector() {
        // Utility class
    }
}
