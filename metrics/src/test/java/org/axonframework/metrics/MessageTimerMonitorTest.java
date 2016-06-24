/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertArrayEquals;

public class MessageTimerMonitorTest {

    @Test
    public void testSuccessMessage(){
        TestClock testClock = new TestClock();
        MessageTimerMonitor testSubject = new MessageTimerMonitor(testClock);
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.reportSuccess();

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Timer all = (Timer) metricSet.get("allTimer");
        Timer successTimer = (Timer) metricSet.get("successTimer");
        Timer failureTimer = (Timer) metricSet.get("failureTimer");
        Timer ignoredTimer = (Timer) metricSet.get("ignoredTimer");

        assertArrayEquals(new long[]{1000000}, all.getSnapshot().getValues());
        assertArrayEquals(new long[]{1000000}, successTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, failureTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, ignoredTimer.getSnapshot().getValues());
    }

    @Test
    public void testFailureMessage(){
        TestClock testClock = new TestClock();
        MessageTimerMonitor testSubject = new MessageTimerMonitor(testClock);
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.reportFailure(null);

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Timer all = (Timer) metricSet.get("allTimer");
        Timer successTimer = (Timer) metricSet.get("successTimer");
        Timer failureTimer = (Timer) metricSet.get("failureTimer");
        Timer ignoredTimer = (Timer) metricSet.get("ignoredTimer");

        assertArrayEquals(new long[]{1000000}, all.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, successTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, ignoredTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{1000000}, failureTimer.getSnapshot().getValues());
    }

    @Test
    public void testIgnoredMessage(){
        TestClock testClock = new TestClock();
        MessageTimerMonitor testSubject = new MessageTimerMonitor(testClock);
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.reportIgnored();

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Timer all = (Timer) metricSet.get("allTimer");
        Timer successTimer = (Timer) metricSet.get("successTimer");
        Timer failureTimer = (Timer) metricSet.get("failureTimer");
        Timer ignoredTimer = (Timer) metricSet.get("ignoredTimer");

        assertArrayEquals(new long[]{1000000}, all.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, successTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{1000000}, ignoredTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, failureTimer.getSnapshot().getValues());
    }

}
