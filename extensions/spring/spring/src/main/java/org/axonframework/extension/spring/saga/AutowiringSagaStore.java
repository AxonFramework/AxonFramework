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

package org.axonframework.extension.spring.saga;

import org.axonframework.common.annotation.Internal;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.AssociationValues;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.util.Objects;
import java.util.Set;

/**
 * A {@link SagaStore} that hands every Saga it reads back the resources Spring would have injected into a newly created
 * one.
 * <p>
 * A Saga annotated {@link org.axonframework.extension.spring.stereotype.Saga @Saga} is a prototype-scoped bean, so an
 * instance that is starting for the first time is autowired by the bean factory that produced it. An instance resumed
 * from its store is not: it is reconstructed by the {@link org.axonframework.conversion.Converter Converter} from the
 * stored state, and nothing in that path consults the application context. Without this decorator, a Saga that reaches
 * Axon Framework 5 with events still in flight would find its {@code @Autowired} fields null on every event after the
 * first, which is the situation the {@code axon-legacy} module exists to avoid.
 * <p>
 * Injection is annotation-driven, through
 * {@link AutowireCapableBeanFactory#autowireBeanProperties(Object, int, boolean)} with
 * {@link AutowireCapableBeanFactory#AUTOWIRE_NO} and no dependency check. That is deliberately the same call Axon
 * Framework 4's {@code SpringResourceInjector} made, so a field carrying no annotation is left alone, an
 * {@code @Autowired(required = false)} member nothing satisfies stays null, and an unsatisfiable required member is
 * reported rather than silently skipped. Bean lifecycle callbacks such as {@code @PostConstruct} are not invoked, as
 * they were not in Axon Framework 4 either.
 * <p>
 * Applied per Saga by the Spring configuration, which wraps whichever store that Saga resolves to. Applications do not
 * construct this.
 *
 * @param <T> the type of Saga kept in this store
 * @author Mateusz Nowak
 * @since 5.4.0
 */
@Internal
public class AutowiringSagaStore<T> implements SagaStore<T> {

    private final SagaStore<T> delegate;
    private final AutowireCapableBeanFactory beanFactory;

    /**
     * Constructs an {@code AutowiringSagaStore} autowiring the Sagas read from the given {@code delegate} through the
     * given {@code beanFactory}.
     *
     * @param delegate    the store the Sagas are kept in
     * @param beanFactory the bean factory injecting the resources a Saga declares
     */
    public AutowiringSagaStore(SagaStore<T> delegate, AutowireCapableBeanFactory beanFactory) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate SagaStore may not be null.");
        this.beanFactory = Objects.requireNonNull(beanFactory, "The beanFactory may not be null.");
    }

    @Override
    public Set<String> findSagas(Class<? extends T> sagaType, AssociationValue associationValue) {
        return delegate.findSagas(sagaType, associationValue);
    }

    @Override
    public @Nullable <S extends T> Entry<S> loadSaga(Class<S> sagaType, String sagaIdentifier) {
        Entry<S> entry = delegate.loadSaga(sagaType, sagaIdentifier);
        if (entry == null) {
            // A Saga may cease to exist between being found by association and being loaded, which the contract of
            // loadSaga answers with null rather than an exception.
            return null;
        }
        beanFactory.autowireBeanProperties(entry.saga(), AutowireCapableBeanFactory.AUTOWIRE_NO, false);
        return entry;
    }

    @Override
    public void deleteSaga(Class<? extends T> sagaType, String sagaIdentifier,
                           Set<AssociationValue> associationValues) {
        delegate.deleteSaga(sagaType, sagaIdentifier, associationValues);
    }

    @Override
    public void insertSaga(Class<? extends T> sagaType, String sagaIdentifier, T saga,
                           Set<AssociationValue> associationValues) {
        delegate.insertSaga(sagaType, sagaIdentifier, saga, associationValues);
    }

    @Override
    public void updateSaga(Class<? extends T> sagaType, String sagaIdentifier, T saga,
                           AssociationValues associationValues) {
        delegate.updateSaga(sagaType, sagaIdentifier, saga, associationValues);
    }
}
