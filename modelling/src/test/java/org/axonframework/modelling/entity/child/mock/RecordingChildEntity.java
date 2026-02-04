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

package org.axonframework.modelling.entity.child.mock;

import java.util.List;

public class RecordingChildEntity extends RecordingEntity<RecordingChildEntity> {

    private final String id;

    public RecordingChildEntity() {
        this("base-id", List.of());
    }

    public RecordingChildEntity(String id) {
        this(id, List.of());
    }

    public RecordingChildEntity(String id, List<String> evolves) {
        super("ChildEntity", evolves);
        this.id = id;
    }

    @Override
    protected RecordingChildEntity createNewInstance(List<String> evolves) {
        return new RecordingChildEntity(id, evolves);
    }

    public String getId() {
        return id;
    }
}
