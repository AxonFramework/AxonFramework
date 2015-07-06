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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Allard Buijze
 */
public class MatchAllFieldFilterTest {

    private String field;

    @Test
    public void testAcceptWhenEmpty() throws Exception {
        assertTrue(new MatchAllFieldFilter(Collections.<FieldFilter>emptyList())
                           .accept(MatchAllFieldFilterTest.class.getDeclaredField("field")));
    }

    @Test
    public void testAcceptWhenAllAccept() throws Exception {
        assertTrue(new MatchAllFieldFilter(Arrays.<FieldFilter>asList(AllFieldsFilter.instance(),
                                                                      AllFieldsFilter.instance()))
                           .accept(MatchAllFieldFilterTest.class.getDeclaredField("field")));
    }

    @Test
    public void testRejectWhenOneRejects() throws Exception {
        assertFalse(new MatchAllFieldFilter(Arrays.asList(AllFieldsFilter.instance(), new FieldFilter() {

            @Override
            public boolean accept(Field field) {
                return false;
            }
        }))
                            .accept(MatchAllFieldFilterTest.class.getDeclaredField("field")));
    }
}