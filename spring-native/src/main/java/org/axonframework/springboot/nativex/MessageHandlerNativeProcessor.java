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

package org.axonframework.springboot.nativex;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.annotation.MessageHandler;
import org.axonframework.spring.config.MessageHandlerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.BeanFactoryNativeConfigurationProcessor;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.NativeConfigurationRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.nativex.hint.TypeAccess;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.List;

import static org.axonframework.spring.config.MessageHandlerLookup.messageHandlerBeans;

/**
 * A {@link BeanFactoryNativeConfigurationProcessor} implementation registering reflective access on {@link
 * org.axonframework.messaging.Message} handling components build with Axon Framework.
 * <p>
 * This component discovers the message handling components to register through the {@link
 * org.axonframework.spring.config.MessageHandlerLookup#messageHandlerBeans(Class, ConfigurableListableBeanFactory,
 * boolean)} method.
 * <p>
 * Note that this service is part of Axon's <b>experimental</b> release.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
public class MessageHandlerNativeProcessor implements BeanFactoryNativeConfigurationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MessageHandlerNativeProcessor.class);

    @Override
    public void process(ConfigurableListableBeanFactory beanFactory, NativeConfigurationRegistry registry) {
        NativeConfigurationRegistry.ReflectionConfiguration reflection = registry.reflection();
        MessageHandlerConfigurer.Type[] types = MessageHandlerConfigurer.Type.values();

        for (MessageHandlerConfigurer.Type messageType : types) {
            List<String> beanNames = messageHandlerBeans(messageType.getMessageType(), beanFactory, true);
            logger.info("Found #{} beans for [{}] requiring reflective access.", beanNames.size(), messageType);
            for (String beanName : beanNames) {
                Class<?> type = beanFactory.getType(beanName);
                if (type != null) {
                    logger.debug("Registering native access to handling component [{}].", type.getSimpleName());
                    reflection.forType(type).withAccess(
                            TypeAccess.DECLARED_FIELDS, TypeAccess.DECLARED_CONSTRUCTORS, TypeAccess.DECLARED_METHODS
                    );

                    for (Method method : ReflectionUtils.methodsOf(type)) {
                        registerParameterReflection(reflection, method);
                    }
                    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                        registerParameterReflection(reflection, constructor);
                    }
                } else {
                    logger.error("Unable to resolve type for bean [{}]. "
                                         + "Handler methods are probably not available at runtime.", beanName);
                }
            }
        }
    }

    private void registerParameterReflection(NativeConfigurationRegistry.ReflectionConfiguration reflection,
                                             Executable executable) {
        if (!AnnotationUtils.isAnnotationPresent(executable, MessageHandler.class)) {
            return;
        }

        reflection.forType(executable.getDeclaringClass()).withExecutables(executable);
        for (Class<?> parameterType : executable.getParameterTypes()) {
            reflection.forType(parameterType).withAccess(TypeAccess.PUBLIC_CLASSES,
                                                         TypeAccess.DECLARED_CONSTRUCTORS,
                                                         TypeAccess.DECLARED_METHODS,
                                                         TypeAccess.DECLARED_FIELDS);
        }

        if (executable instanceof Method) {
            Class<?> returnType = ((Method) executable).getReturnType();
            if (!returnType.isPrimitive()) {
                reflection.forType(returnType).withAccess(TypeAccess.PUBLIC_CLASSES,
                                                          TypeAccess.DECLARED_CONSTRUCTORS,
                                                          TypeAccess.DECLARED_METHODS,
                                                          TypeAccess.DECLARED_FIELDS);
            }
        }
    }
}
