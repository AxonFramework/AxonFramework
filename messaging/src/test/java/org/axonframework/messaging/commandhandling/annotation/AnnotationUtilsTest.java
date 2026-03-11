/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.commandhandling.annotation;

import org.axonframework.messaging.core.annotation.AggregateType;
import org.junit.jupiter.api.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Optional;

import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link org.axonframework.common.annotation.AnnotationUtils}.
 *
 * @author Simon Zambrovski
 */
class AnnotationUtilsTest {

    @Test
    void findAttributesOnNonExistentAnnotation() throws NoSuchMethodException {
        Optional<Map<String, Object>> result =
                findAnnotationAttributes(getClass().getDeclaredMethod("dynamicallyOverridden"), AggregateType.class);
        assertFalse(result.isPresent(), "Didn't expect attributes to be found for non-existent annotation");
    }

    @DynamicOverrideAnnotated(property = "dynamic-override")
    public void dynamicallyOverridden() {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
    @TheTarget
    public @interface DynamicOverrideAnnotated {

        String property();

        String extraValue() default "extra";

        String theTarget() default "otherValue";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
    public @interface TheTarget {

        String property() default "value";

        String value() default "value()";
    }
}
