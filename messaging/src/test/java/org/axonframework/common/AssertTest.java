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

package org.axonframework.common;

import org.junit.Test;

/**
 * @author Allard Buijze
 */
public class AssertTest {

    @Test
    public void testState_Accept() {
        Assert.state(true, () -> "Hello");
    }

    @Test(expected = IllegalStateException.class)
    public void testState_Fail() {
        Assert.state(false, () -> "Hello");
    }

    @Test
    public void testIsTrue_Accept() {
        Assert.isTrue(true, () -> "Hello");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsTrue_Fail() {
        org.axonframework.common.Assert.isTrue(false, () -> "Hello");
    }
}
