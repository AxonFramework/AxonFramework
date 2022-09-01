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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Allard Buijze
 */
class AssertTest {

    @Test
    void state_Accept() {
        Assert.state(true, () -> "Hello");
    }

    @Test
    void state_Fail() {
        assertThrows(IllegalStateException.class, () -> Assert.state(false, () -> "Hello"));
    }

    @Test
    void isTrue_Accept() {
        Assert.isTrue(true, () -> "Hello");
    }

    @Test
    void isTrue_Fail() {
        assertThrows(IllegalArgumentException.class, () -> Assert.isTrue(false, () -> "Hello"));
    }
}
