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

package org.axonframework.saga.annotation;

import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.AssociationValues;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Allard Buijze
 */
public class AssociationValuesImpl implements AssociationValues, Serializable {

    private static final long serialVersionUID = 8273718165811296962L;

    private Set<AssociationValue> values = new HashSet<AssociationValue>();
    private transient Set<ChangeListener> handlers = new HashSet<ChangeListener>();

    @Override
    public void addChangeListener(ChangeListener changeListener) {
        handlers.add(changeListener);
    }

    @Override
    public void removeChangeListener(ChangeListener changeListener) {
        handlers.remove(changeListener);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return values.contains(o);
    }

    @Override
    public Iterator<AssociationValue> iterator() {
        return Collections.unmodifiableSet(values).iterator();
    }

    @Override
    public Object[] toArray() {
        return values.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return values.toArray(a);
    }

    @Override
    public boolean add(AssociationValue associationValue) {
        boolean added = values.add(associationValue);
        if (added) {
            for (ChangeListener cl : handlers) {
                cl.onAssociationValueAdded(associationValue);
            }
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = values.remove(o);
        if (removed) {
            for (ChangeListener cl : handlers) {
                cl.onAssociationValueRemoved((AssociationValue) o);
            }
        }
        return removed;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return values.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends AssociationValue> c) {
        boolean added = false;
        for (AssociationValue val : c) {
            added |= add(val);
        }
        return added;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return values.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return values.removeAll(c);
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public boolean equals(Object o) {
        return values.equals(o);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    // Java Serialization methods

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        handlers = new HashSet<ChangeListener>();
    }

}
