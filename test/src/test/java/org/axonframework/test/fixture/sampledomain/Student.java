/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.axonframework.eventsourcing.annotations.EventSourcingHandler;

/**
 * Event-sourced Student model
 */
public class Student {

    private String id;
    private String name;
    private Integer changes = 0;

    public Student(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getChanges() {
        return changes;
    }

    @EventSourcingHandler
    public void on(StudentNameChangedEvent event) {
        this.name = event.name();
        this.changes = event.change();
    }
}
