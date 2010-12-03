/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.saga.timer;

import org.axonframework.domain.ApplicationEvent;
import org.axonframework.saga.Saga;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class ScheduledEvent extends ApplicationEvent {

    public ScheduledEvent(Saga source, Duration schedule) {
        this(source, new DateTime().plus(schedule));
    }

    public ScheduledEvent(Saga source, DateTime schedule) {
        super(source);
        addMetaData("scheduledTime", schedule);
    }

    public DateTime getScheduledTime() {
        return (DateTime) getMetaDataValue("scheduledTime");
    }
}
