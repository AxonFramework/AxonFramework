/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.modelling.saga.metamodel;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.saga.AssociationValue;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SagaModelTest {

    @Test
    void testDefaultMessageHandlerInterceptorMemberChain() {
        SagaModel<?> instance = new SomeSagaModel<>();
        assertEquals(SagaModel.NoMoreInterceptors.class, instance.chainedInterceptor().getClass());
    }

    private static class SomeSagaModel<T> implements SagaModel<T> {

        @Override
        public Optional<AssociationValue> resolveAssociation(EventMessage<?> eventMessage) {
            return Optional.empty();
        }

        @Override
        public List<MessageHandlingMember<? super T>> findHandlerMethods(EventMessage<?> event) {
            return null;
        }

        @Override
        public SagaMetaModelFactory modelFactory() {
            return null;
        }
    }
}
