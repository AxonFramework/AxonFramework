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

package org.axonframework.test.matchers;

import org.axonframework.test.FixtureExecutionException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allard Buijze
 */
class IgnoreFieldTest {

    @SuppressWarnings("unused") private String field;
    @SuppressWarnings("unused") private String ignoredField;

    @Test
    void testAcceptOtherFields_ClassStringConstructor() throws Exception {
        IgnoreField testSubject = new IgnoreField(IgnoreFieldTest.class, "ignoredField");
        assertTrue(testSubject.accept(IgnoreFieldTest.class.getDeclaredField("field")));
        assertFalse(testSubject.accept(IgnoreFieldTest.class.getDeclaredField("ignoredField")));
    }

    @Test
    void testAcceptOtherFields_FieldConstructor() throws Exception {
        IgnoreField testSubject = new IgnoreField(IgnoreFieldTest.class.getDeclaredField("ignoredField"));
        assertTrue(testSubject.accept(IgnoreFieldTest.class.getDeclaredField("field")));
        assertFalse(testSubject.accept(IgnoreFieldTest.class.getDeclaredField("ignoredField")));
    }

    @Test
    void testRejectNonExistentField() {
        assertThrows(FixtureExecutionException.class, () -> new IgnoreField(IgnoreFieldTest.class, "nonExistent"));
    }
}
