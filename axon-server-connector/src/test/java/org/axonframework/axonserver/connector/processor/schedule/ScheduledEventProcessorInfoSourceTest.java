/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector.processor.schedule;

import org.axonframework.axonserver.connector.processor.FakeEventProcessorInfoSource;
import org.axonframework.axonserver.connector.utils.AssertUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created by Sara Pellegrini on 23/03/2018.
 * sara.pellegrini@gmail.com
 */
class ScheduledEventProcessorInfoSourceTest {

    private ScheduledEventProcessorInfoSource scheduled;
    private FakeEventProcessorInfoSource delegate;

    @BeforeEach
    void setUp() throws Exception {
        delegate = new FakeEventProcessorInfoSource();
        scheduled = new ScheduledEventProcessorInfoSource(50, 30, delegate);
    }

    @AfterEach
    void tearDown() throws Exception {
        scheduled.shutdown();
    }

    @Test
    void notifyInformation() throws InterruptedException {
        scheduled.start();
        TimeUnit.MILLISECONDS.sleep(50);
        AssertUtils.assertWithin(100, TimeUnit.MILLISECONDS, () -> assertEquals(2, delegate.notifyCalls()));
    }


}
