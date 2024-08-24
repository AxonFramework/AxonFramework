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
     * Before the Performance optimization(avoiding exceptions) for each event a lot of events were thrown, if the
     * accessors the access do not follow the Bean Standard, but follows the Record Standard. Furthermore, for
     * aggregateId-Properties will be scanned, which also caused exceptions. The performance optimization decreased the
     * CPU time by 65 %.
     * <p/>
     * Before the optimization 100,000 executions had a duration of around 355ms.</br> After the optimization 100,000
     * executions had a duration of around 116ms.
     * <p/>
     * On a MacBook Pro M1 using jdk 21 the duration is around 105-130ms. I am not sure if this test can be used,
     * because I don't know about the build environment and other developer environments.
     */
    @Test
    @Timeout(value = 200, unit = TimeUnit.MILLISECONDS)
    void testPerformanceWhenMethodNotExisting() {
        BeanPropertyAccessStrategy beanPropertyAccess = new BeanPropertyAccessStrategy();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            beanPropertyAccess.propertyFor(TestMessage.class, "notExistingProperty" + i);
        }
        long end = System.currentTimeMillis();
        log.info("Used time: {} nanos", (end - start));
        // 100,000 requests
        // with exception -> 357 millis
        // without exception -> 113 millis
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
