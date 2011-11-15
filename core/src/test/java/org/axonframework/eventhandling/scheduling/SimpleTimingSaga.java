/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventhandling.scheduling;

import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.annotation.SagaEventHandler;
import org.axonframework.saga.annotation.StartSaga;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Allard Buijze
 */
public class SimpleTimingSaga extends AbstractAnnotatedSaga {

    private static final long serialVersionUID = -3190758648834280201L;

    private transient EventScheduler timer;
    private volatile boolean triggered = false;
    private transient CountDownLatch latch = new CountDownLatch(1);
    private static final Duration SCHEDULE_DURATION = new Duration(10);

    @StartSaga
    @SagaEventHandler(associationProperty = "association")
    public void handle(StartingEvent event) {
        timer.schedule(SCHEDULE_DURATION,
                       new MySagaExpiredEvent(event.getAssociation()));
    }

    @SagaEventHandler(associationProperty = "association")
    public void handle(MySagaExpiredEvent event) {
        this.triggered = true;
        latch.countDown();
    }

    public boolean isTriggered() {
        return triggered;
    }

    @Autowired
    public void setTimer(EventScheduler timer) {
        this.timer = timer;
    }

    public void waitForEventProcessing(int timeout) throws InterruptedException {
        latch.await(timeout, TimeUnit.MILLISECONDS);
    }

    // Java Serialization methods

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.latch = new CountDownLatch(1);
        if (triggered) {
            latch.countDown();
        }
    }
}
