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

package org.axonframework.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the relative priority of the annotated component. Components with a higher priority are considered before
 * those with lower priority.
 *
 * @author Allard Buijze
 * @since 2.1
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Priority {

    /**
     * Value indicating the annotated member should come last.
     */
    int LAST = Integer.MIN_VALUE;
    /**
     * Value indicating the annotated member should be placed at the "lower quarter".
     */
    int LOWER = Integer.MIN_VALUE / 2;
    /**
     * Value indicating the annotated member should be placed at the "lower half".
     */
    int LOW = Integer.MIN_VALUE / 4;
    /**
     * Value indicating the annotated member should have medium priority, effectively placing it "in the middle".
     */
    int NEUTRAL = 0;
    /**
     * Value indicating the annotated member should have high priority, effectively placing it "in the first half".
     */
    int HIGH = Integer.MAX_VALUE / 2;

    /**
     * Value indicating the annotated member should be the very first
     */
    int FIRST = Integer.MAX_VALUE;

    /**
     * A value indicating the priority. Members with higher values must come before members with a lower value
     */
    int value() default NEUTRAL;
}
