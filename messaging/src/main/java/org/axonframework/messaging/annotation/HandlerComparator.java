/*
 * Copyright (c) 2010-2018. Axon Framework
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

import java.lang.reflect.Executable;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Comparator used by {@link AnnotatedHandlerInspector} to sort {@link MessageHandlingMember entity members}.
 */
public final class HandlerComparator {

    private static final Comparator<MessageHandlingMember<?>> INSTANCE = Comparator
            .comparing((Function<MessageHandlingMember<?>, Class<?>>) MessageHandlingMember::payloadType,
                       HandlerComparator::compareHierarchy)
            .thenComparing(
                    Comparator.comparingInt((ToIntFunction<MessageHandlingMember<?>>) MessageHandlingMember::priority)
                            .reversed())
            .thenComparing(m -> m.unwrap(Executable.class).map(Executable::toGenericString).orElse(m.toString()));

    // prevent construction
    private HandlerComparator() {
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
        if (Objects.equals(o1, o2)) {
            return 0;
        } else if (o1.isAssignableFrom(o2)) {
            return 1;
        } else if (o2.isAssignableFrom(o1)) {
            return -1;
        }
        return Integer.compare(depthOf(o2), depthOf(o1));
    }

    private static int depthOf(Class<?> o1) {
        int depth = 0;
        Class<?> type = o1;
        while (type != null && !Object.class.equals(type)) {
            depth++;
            type = type.getSuperclass();
        }
        if (o1.isAnnotation()) {
            depth += 1000;
        }
        return depth;
    }

}
