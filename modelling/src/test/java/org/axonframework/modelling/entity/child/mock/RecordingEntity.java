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

import java.util.LinkedList;
import java.util.List;

public abstract class RecordingEntity<S extends RecordingEntity<S>> {

    private final String name;
    private final List<String> evolves;

    protected RecordingEntity(String name, List<String> evolves) {
        this.name = name;
        this.evolves = evolves;
    }

    public S evolve(String description) {
        LinkedList<String> list = new LinkedList<>(evolves);
        list.addFirst(description);
        return createNewInstance(list);
    }

    protected abstract S createNewInstance(List<String> evolves);

    public List<String> getEvolves() {
        return evolves;
    }

    @Override
    public String toString() {
        return name;
    }
}
