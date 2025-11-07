/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.junit.jupiter.api.*;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ObjectUtils}.
 *
 * @author Steven van Beelen
 */
class ObjectUtilsTest {

    private static final String NULL_INSTANCE = null;
    private static final String INSTANCE = "instance";
    private static final String DEFAULT_VALUE = "default";

    @Test
    void getOrDefaultUsingValueProvider() {
        Function<String, String> valueProvider = o -> o;
        assertEquals(DEFAULT_VALUE, ObjectUtils.getOrDefault(NULL_INSTANCE, valueProvider, DEFAULT_VALUE));
        assertEquals(INSTANCE, ObjectUtils.getOrDefault(INSTANCE, valueProvider, DEFAULT_VALUE));
    }

    @Test
    void supplySameInstance() {
        Supplier<Object> testSubject = ObjectUtils.sameInstanceSupplier(Object::new);
        assertSame(testSubject.get(), testSubject.get());
    }
}