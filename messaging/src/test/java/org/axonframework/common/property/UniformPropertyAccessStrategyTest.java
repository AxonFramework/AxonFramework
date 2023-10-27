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

package org.axonframework.common.property;


import static org.junit.jupiter.api.Assertions.fail;

public class UniformPropertyAccessStrategyTest
        extends AbstractPropertyAccessStrategyTest<UniformPropertyAccessStrategyTest.TestMessage> {

    @Override
    protected String exceptionPropertyName() {
        return "exceptionProperty";
    }

    @Override
    protected String regularPropertyName() {
        return "actualProperty";
    }

    @Override
    protected String unknownPropertyName() {
        return "bogusProperty";
    }

    @Override
    protected TestMessage propertyHoldingInstance() {
        return new TestMessage();
    }

    @Override
    protected Property<TestMessage> getProperty(String property) {
        return new UniformPropertyAccessStrategy().propertyFor(TestMessage.class, property);
    }

    @Override
    protected String voidPropertyName() {
        return "voidMethod";
    }

    @SuppressWarnings("UnusedDeclaration")
    static class TestMessage {

        public String actualProperty() {
            return "value";
        }

        public String exceptionProperty() {
            throw new RuntimeException("GetTestException");
        }

        public void voidMethod() {
            fail("This method should never be invoked");
        }
    }
}
