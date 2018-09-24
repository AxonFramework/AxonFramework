/*
 * Copyright (c) 2018. AxonIQ
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
import org.junit.*;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Created by Sara Pellegrini on 23/03/2018.
 * sara.pellegrini@gmail.com
 */
public class ScheduledEventProcessorInfoSourceTest {

    @Test
    public void notifyInformation() throws InterruptedException {
        FakeEventProcessorInfoSource delegate = new FakeEventProcessorInfoSource();
        ScheduledEventProcessorInfoSource scheduled = new ScheduledEventProcessorInfoSource(50, 30, delegate);
        scheduled.start();
        TimeUnit.MILLISECONDS.sleep(105);
        assertEquals(2, delegate.notifyCalls());
    }


}