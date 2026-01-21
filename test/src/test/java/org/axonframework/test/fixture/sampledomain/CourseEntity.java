/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.test.fixture.sampledomain;

import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

@EventSourcedEntity
public class CourseEntity {

    public static EventSourcingConfigurer configurer() {
        return EventSourcingConfigurer.create()
                .registerEntity(
                        EventSourcedEntityModule.autodetected(Integer.class, CourseEntity.class)
                );
    }

    @CommandHandler
    static void handle(CreateCourse cmd, EventAppender eventAppender) {
        eventAppender.append(new CourseCreated(cmd.courseId(), cmd.name()));
    }

    private final int courseId;
    private final String name;

    @EntityCreator
    public CourseEntity(CourseCreated event) {
        this.courseId = event.courseId();
        this.name = event.name();
    }
}
