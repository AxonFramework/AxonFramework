/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

public class BeanPropertyAccessStrategyTest extends
        AbstractPropertyAccessStrategyTest<BeanPropertyAccessStrategyTest.TestMessage> {

    private static final Logger log = LoggerFactory.getLogger(BeanPropertyAccessStrategyTest.class);

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
        return new BeanPropertyAccessStrategy().propertyFor(TestMessage.class, property);
    }

    /**
     * The build server environment is very slow. Currently there is no strategy when and how to test the
     * performance of this class. However, maybe in the future this code will be helpfull for someone.
     * <p/>
     * The suggested way to disable this test for the moment was to drop the test annotation.
     * <p/>
     * The goal this test should be to keep the code fast. This can currently not be achieved, because
     * the build servers are "slow".
     * <p/>
     * Facts on a MacBook Pro M1:<p/>
     * Duration of the Test
     * <ul>
     *     <li>before the performance fix: 355ms<p/></li>
     *     <li>after the performance fix: 116ms<p/></li>
     * </ul>
     */
//    @Test
//    @Timeout(value = 200, unit = TimeUnit.MILLISECONDS)
    @SuppressWarnings("unused")
    void testPerformanceWhenMethodNotExisting() {
        BeanPropertyAccessStrategy beanPropertyAccess = new BeanPropertyAccessStrategy();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            beanPropertyAccess.propertyFor(TestMessage.class, "notExistingProperty" + i);
        }
        long end = System.currentTimeMillis();
        log.info("Used time: {} nanos", (end - start));
    }

    protected String voidPropertyName() {
        return "voidMethod";
    }

    @SuppressWarnings("UnusedDeclaration")
    class TestMessage {

        public String getActualProperty() {
            return "propertyValue";
        }

        public String getExceptionProperty() {
            throw new RuntimeException("GetTestException");
        }

        public void getVoidMethod() {
            fail("This method should never be invoked");
        }
    }
}
