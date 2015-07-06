/*
 * Copyright (c) 2010-2015. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.matchers;

import org.junit.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Allard Buijze
 */
public class NonTransientFieldsFilterTest {

    @SuppressWarnings("unused")
    private transient String transientField;
    @SuppressWarnings("unused")
    private String nonTransientField;

    @Test
    public void testAcceptNonTransientField() throws Exception {
        assertTrue(NonTransientFieldsFilter.instance()
                                           .accept(getClass().getDeclaredField("nonTransientField")));
    }

    @Test
    public void testRejectTransientField() throws Exception {
        assertFalse(NonTransientFieldsFilter.instance()
                                            .accept(getClass().getDeclaredField("transientField")));
    }
}