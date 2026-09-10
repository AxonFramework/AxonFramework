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

/**
 * The Axon Framework 4 {@code PaymentSaga} itself, ported through {@code axon-legacy} with minimal changes.
 * <p>
 * This is not a fifth recipe alongside {@code saga.repository}, {@code saga.injectentity}, {@code saga.eventsourced}
 * and {@code saga.verticalslices}: it does not run {@link org.axonframework.examples.sagarecipes.saga.SagaRecipeContractTest},
 * because {@code axon-legacy} has not yet ported deadlines (see the class-level Javadoc of
 * {@link org.axonframework.examples.sagarecipes.legacy.PaymentSaga}), so it cannot satisfy the scenarios that only
 * exist because the other four recipes have no {@code DeadlineManager} to lean on. It exists purely as a migration
 * reference: what the original Axon Framework 4 code looked like, running unmodified in spirit against this
 * module's own domain, next to what it becomes in each of the four recipes.
 *
 * @author Mateusz Nowak
 * @since 5.4.0
 */
package org.axonframework.examples.sagarecipes.legacy;
