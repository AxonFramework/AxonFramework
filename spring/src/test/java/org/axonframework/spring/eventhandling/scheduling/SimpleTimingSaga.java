/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.spring.eventhandling.scheduling;

import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.annotation.SagaEventHandler;
import org.axonframework.saga.annotation.StartSaga;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Allard Buijze
 */
public class SimpleTimingSaga extends AbstractAnnotatedSaga {

    private static final long serialVersionUID = -3190758648834280201L;

    private transient EventScheduler timer;
    private volatile boolean triggered = false;
    private static final Duration SCHEDULE_DURATION = new Duration(50);

    @StartSaga
    @SagaEventHandler(associationProperty = "association")
    public void handle(StartingEvent event) {
        timer.schedule(SCHEDULE_DURATION,
                       new MySagaExpiredEvent(event.getAssociation()));
    }

    @SagaEventHandler(associationProperty = "association")
    public void handle(MySagaExpiredEvent event) {
        this.triggered = true;
    }

    public boolean isTriggered() {
        return triggered;
    }

    @Autowired
    public void setTimer(EventScheduler timer) {
        this.timer = timer;
    }

}
