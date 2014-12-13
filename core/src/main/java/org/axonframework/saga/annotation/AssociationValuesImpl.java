/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.saga.annotation;

import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.AssociationValues;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Default implementation of the AssociationValues interface. This implementation is fully serializable.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AssociationValuesImpl implements AssociationValues, Serializable {

    private static final long serialVersionUID = 8273718165811296962L;

    private final Set<AssociationValue> values = new CopyOnWriteArraySet<>();
    private transient Set<AssociationValue> addedValues = new HashSet<>();
    private transient Set<AssociationValue> removedValues = new HashSet<>();

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean contains(AssociationValue associationValue) {
        return values.contains(associationValue);
    }

    @Override
    public Iterator<AssociationValue> iterator() {
        return Collections.unmodifiableSet(values).iterator();
    }

    @Override
    public boolean add(AssociationValue associationValue) {
        final boolean added = values.add(associationValue);
        if (added) {
                initializeChangeTrackers();
                if (!removedValues.remove(associationValue)) {
                    addedValues.add(associationValue);
                }
        }
        return added;
    }

    @Override
    public boolean remove(AssociationValue associationValue) {
        final boolean removed = values.remove(associationValue);
        if (removed) {
                initializeChangeTrackers();
                if (!addedValues.remove(associationValue)) {
                    removedValues.add(associationValue);
            }
        }
        return removed;
    }

    private void initializeChangeTrackers() {
        if (removedValues == null) {
            removedValues = new HashSet<>();
        }
        if (addedValues == null) {
            addedValues = new HashSet<>();
        }
    }

    @Override
    public Set<AssociationValue> asSet() {
        return Collections.unmodifiableSet(values);
    }

    @Override
    public Set<AssociationValue> removedAssociations() {
        if (removedValues == null || removedValues.isEmpty()) {
            return Collections.emptySet();
        }
        return removedValues;
    }

    @Override
    public Set<AssociationValue> addedAssociations() {
        if (addedValues == null || addedValues.isEmpty()) {
            return Collections.emptySet();
        }
        return addedValues;
    }

    @Override
    public void commit() {
        if (addedValues != null) {
            addedValues.clear();
        }
        if (removedValues != null) {
            removedValues.clear();
        }
    }
}
