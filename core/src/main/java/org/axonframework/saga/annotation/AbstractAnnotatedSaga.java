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

import org.axonframework.domain.Event;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaLookupProperty;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of the {@link Saga interface} that delegates incoming events to {@link
 * org.axonframework.saga.annotation.SagaEventHandler @SagaEventHandler} annotated methods.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractAnnotatedSaga implements Saga, Serializable {

    private final Set<SagaLookupProperty> lookupProperties;
    private final String identifier;
    private transient volatile SagaEventHandlerInvoker eventHandlerInvoker;
    private volatile boolean isActive = true;

    /**
     * Initialize the saga with a random identifier. The identifier used is a randomly generated UUID.
     */
    protected AbstractAnnotatedSaga() {
        this(UUID.randomUUID().toString());
    }

    /**
     * Initialize the saga with the given identifier.
     *
     * @param identifier the identifier to initialize the saga with.
     */
    protected AbstractAnnotatedSaga(String identifier) {
        this.identifier = identifier;
        lookupProperties = new HashSet<SagaLookupProperty>();
        eventHandlerInvoker = new SagaEventHandlerInvoker(this);
    }

    @Override
    public String getSagaIdentifier() {
        return identifier;
    }

    @Override
    public Set<SagaLookupProperty> getLookupProperties() {
        return Collections.unmodifiableSet(lookupProperties);
    }

    @Override
    public final void handle(Event event) {
        eventHandlerInvoker.invokeSagaEventHandlerMethod(event);
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    /**
     * Marks the saga as ended. Ended saga's may be cleaned up by the repository when they are committed.
     */
    protected void end() {
        isActive = false;
    }

    /**
     * Registers a SagaLookupProperty with the given saga. When the saga is committed, it can be found using the
     * registered property.
     *
     * @param property The lookup property to assign to this saga.
     */
    protected void registerLookupProperty(SagaLookupProperty property) {
        lookupProperties.add(property);
    }

    /**
     * Removes the given SagaLookup property from this Saga. When the saga is committed, it can no longer be found using
     * the given property. If the given property wasn't registered with the saga, nothing happens.
     *
     * @param property the lookup property to remove from the saga.
     */
    protected void removeLookupProperty(SagaLookupProperty property) {
        lookupProperties.remove(property);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        eventHandlerInvoker = new SagaEventHandlerInvoker(this);
    }
}
