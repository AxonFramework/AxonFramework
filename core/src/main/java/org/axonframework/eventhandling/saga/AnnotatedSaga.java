/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.eventhandling.saga;

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.saga.metamodel.SagaModel;
import org.axonframework.eventsourcing.eventstore.TrackingToken;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implementation of the {@link Saga interface} that allows for a POJO instance with annotated message handlers to act
 * as a Saga. The POJO instance can access static methods on {@link SagaLifecycle} as long as it is access via the
 * {@link #execute(Consumer)} or {@link #invoke(Function)} methods.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class AnnotatedSaga<T> extends SagaLifecycle implements Saga<T> {

    private final SagaModel<T> metaModel;

    private final AssociationValues associationValues;
    private volatile boolean isActive = true;
    private final String sagaId;
    private final T sagaInstance;
    private final AtomicReference<TrackingToken> trackingToken;

    /**
     * Creates an AnnotatedSaga instance to wrap the given {@code annotatedSaga}, identifier with the given
     * {@code sagaId} and associated with the given {@code associationValues}. The {@code metaModel} provides the
     * description of the structure of the Saga.
     *
     * @param sagaId            The identifier of this Saga instance
     * @param associationValues The current associations of this Saga
     * @param annotatedSaga     The object instance representing the Saga
     * @param trackingToken     The token identifying the position in a stream the saga has last processed
     * @param metaModel         The model describing Saga structure
     */
    public AnnotatedSaga(String sagaId, Set<AssociationValue> associationValues,
                         T annotatedSaga, TrackingToken trackingToken, SagaModel<T> metaModel) {
        Assert.notNull(annotatedSaga, () -> "SagaInstance may not be null");
        this.sagaId = sagaId;
        this.associationValues = new AssociationValuesImpl(associationValues);
        this.sagaInstance = annotatedSaga;
        this.metaModel = metaModel;
        this.trackingToken = new AtomicReference<>(trackingToken);
    }

    @Override
    public String getSagaIdentifier() {
        return sagaId;
    }

    @Override
    public AssociationValues getAssociationValues() {
        return associationValues;
    }

    @Override
    public <R> R invoke(Function<T, R> invocation) {
        try {
            return executeWithResult(() -> invocation.apply(sagaInstance));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SagaExecutionException("Exception while invoking a Saga", e);
        }
    }

    @Override
    public void execute(Consumer<T> invocation) {
        super.execute(() -> invocation.accept(sagaInstance));
    }

    @Override
    public final boolean handle(EventMessage<?> event) {
        if (isActive) {
            return metaModel.findHandlerMethods(event).stream()
                    .filter(h -> getAssociationValues().contains(h.getAssociationValue(event)))
                    .findFirst().map(h -> {
                        try {
                            executeWithResult(() -> h.handle(event, sagaInstance));
                            if (event instanceof TrackedEventMessage) {
                                this.trackingToken.set(((TrackedEventMessage) event).trackingToken());
                            }
                        } catch (RuntimeException | Error e) {
                            throw e;
                        } catch (Exception e) {
                            throw new SagaExecutionException("Exception while handling an Event in a Saga", e);
                        } finally {
                            if (h.isEndingHandler()) {
                                doEnd();
                            }
                        }
                        return true;
                    })
                    .orElse(false);
        }
        return false;
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public TrackingToken trackingToken() {
        return trackingToken.get();
    }

    /**
     * Returns the (annotated) Saga instance. This method should not be used to modify the Saga's state, as it doesn't
     * allow the instance to access the static methods on {@link SagaLifecycle}.
     *
     * @return the Saga instance
     */
    public T root() {
        return sagaInstance;
    }

    /**
     * Marks the saga as ended. Ended saga's may be cleaned up by the repository when they are committed.
     */
    protected void doEnd() {
        isActive = false;
    }

    /**
     * Registers a AssociationValue with the given saga. When the saga is committed, it can be found using the
     * registered property.
     *
     * @param property The value to associate this saga with.
     */
    protected void doAssociateWith(AssociationValue property) {
        associationValues.add(property);
    }

    /**
     * Removes the given association from this Saga. When the saga is committed, it can no longer be found using the
     * given association. If the given property wasn't registered with the saga, nothing happens.
     *
     * @param property the association value to remove from the saga.
     */
    protected void doRemoveAssociation(AssociationValue property) {
        associationValues.remove(property);
    }

}
