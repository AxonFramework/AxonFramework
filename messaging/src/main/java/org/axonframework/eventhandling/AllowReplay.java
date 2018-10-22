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

package org.axonframework.eventhandling;

import java.lang.annotation.*;

/**
 * Annotation marking a Handler (or class) as being capable of handling replays, or not, depending on the value
 * passed.
 * <p>
 * When placed on the type (class) level, the setting applies to all handlers that don't explicitly override it
 * on the method level.
 * <p>
 * Marking methods as not allowing replay will not change the routing of a message (i.e. will not invoke another
 * handler method). Messages that would otherwise be handled by such handler are simply ignored.
 *
 * @author Allard Buijze
 * @since 3.2
 */
@Documented
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowReplay {

    /**
     * Whether to allow replays on this handler, or not. Defaults to {@code true}
     *
     * @return Whether to allow replays on this handler, or not
     */
    boolean value() default true;
}
