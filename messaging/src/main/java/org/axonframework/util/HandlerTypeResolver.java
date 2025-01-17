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

package org.axonframework.util;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.queryhandling.annotation.QueryHandler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.axonframework.common.ReflectionUtils.methodsOf;
import static org.axonframework.common.annotation.AnnotationUtils.isAnnotationPresent;

/**
 * Uses reflection to know if a handler handles a certain type of messages.
 *
 * @author Gerard Klijs
 * @since 4.6.0
 */
public class HandlerTypeResolver {

    /**
     * Whether this handler has {@link CommandHandler} annotated methods.
     * @param handlerClass the class of the handler
     * @return whether it contains command handler methods
     */
    public static boolean isCommandHandler(Class<?> handlerClass){
        return isHandlerOfType(handlerClass, CommandHandler.class);
    }

    /**
     * Whether this handler has {@link EventHandler} annotated methods.
     * @param handlerClass the class of the handler
     * @return whether it contains event handler methods
     */
    public static boolean isEventHandler(Class<?> handlerClass){
        return isHandlerOfType(handlerClass, EventHandler.class);
    }

    /**
     * Whether this handler has {@link QueryHandler} annotated methods.
     * @param handlerClass the class of the handler
     * @return whether it contains query handler methods
     */
    public static boolean isQueryHandler(Class<?> handlerClass){
        return isHandlerOfType(handlerClass, QueryHandler.class);
    }

    private static boolean isHandlerOfType(Class<?> handlerClass, Class<? extends Annotation> annotationType){
        for (Method m: methodsOf(handlerClass)){
            if(isAnnotationPresent(m, annotationType)){
                return true;
            }
        }
        return false;
    }

    private HandlerTypeResolver() {
        // not to be instantiated
    }
}
