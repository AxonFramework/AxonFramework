/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.messaging.annotation;

import org.axonframework.common.ReversedOrder;

import java.lang.reflect.Executable;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Comparator used by {@link AnnotatedHandlerInspector} to sort {@link MessageHandlingMember} entity members.
 * <p>
 * The ordering among {@code MessageHandlingMember}s through this comparator is defined as follows:
 * <ol type="1">
 * <li>The {@link MessageHandlingMember#priority()}, favoring the largest number</li>
 * <li>The class hierarchy of the {@link MessageHandlingMember#payloadType()}, favoring the most specific handler.</li>
 * <li>The parameter count on the actual message handling function, favoring the highest number as the most specific handler.</li>
 * <li>As a final tie breaker, the {@link Executable#toGenericString()} of the actual message handling function</li>
 * </ol>
 * If the given {@code MessageHandlingMembers} both implement {@link ReversedOrder}, steps 2 through 4 are reversed.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public final class HandlerComparator {

    private static final Comparator<MessageHandlingMember<?>> INSTANCE =
            Comparator.comparingInt((ToIntFunction<MessageHandlingMember<?>>) MessageHandlingMember::priority)
                      .reversed()
                      .thenComparing((memberOne, memberTwo) -> {
                          Comparator<MessageHandlingMember<?>> handlerComparator =
                                  Comparator.comparing(
                                                    (Function<MessageHandlingMember<?>, Class<?>>) MessageHandlingMember::payloadType,
                                                    HandlerComparator::compareHierarchy
                                            )
                                            .thenComparing(Comparator.comparingInt(HandlerComparator::parameterCount)
                                                                     .reversed())
                                            .thenComparing(HandlerComparator::executableName);

                          return memberOne instanceof ReversedOrder && memberTwo instanceof ReversedOrder
                                  ? handlerComparator.reversed().compare(memberOne, memberTwo)
                                  : handlerComparator.compare(memberOne, memberTwo);
                      });

    private HandlerComparator() {
        // Utility class
    }

    /**
     * Returns the singleton comparator managed by the HandlerComparator class.
     *
     * @return the singleton comparator
     */
    public static Comparator<MessageHandlingMember<?>> instance() {
        return INSTANCE;
    }

    private static int compareHierarchy(Class<?> o1, Class<?> o2) {
        if (o1.isInterface() && !o2.isInterface()) {
            return 1;
        } else if (!o1.isInterface() && o2.isInterface()) {
            return -1;
        }
        return Integer.compare(depthOf(o2), depthOf(o1));
    }

    private static int depthOf(Class<?> o1) {
        int depth = 0;
        Class<?> type = o1;
        if (o1.isInterface()) {
            while (type.getInterfaces().length > 0) {
                depth++;
                type = type.getInterfaces()[0];
            }
        } else {
            while (type != null) {
                depth++;
                type = type.getSuperclass();
            }
        }
        if (o1.isAnnotation()) {
            depth += 1000;
        }
        return depth;
    }

    private static Integer parameterCount(MessageHandlingMember<?> handler) {
        return handler.unwrap(Executable.class)
                      .map(Executable::getParameterCount)
                      .orElse(1);
    }

    private static String executableName(MessageHandlingMember<?> handler) {
        return handler.unwrap(Executable.class)
                      .map(Executable::toGenericString)
                      .orElse(handler.toString());
    }
}
