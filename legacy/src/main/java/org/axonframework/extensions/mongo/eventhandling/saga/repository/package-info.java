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
 * A Mongo-backed {@link org.axonframework.modelling.saga.repository.SagaStore}, storing each saga together with its
 * associations as a single document described by
 * {@link org.axonframework.extensions.mongo.eventhandling.saga.repository.SagaEntry}.
 * <p>
 * The document layout is that of Axon Framework 4, so an existing sagas collection can be read and updated without
 * migration, provided its {@code sagaType} field holds saga class names. See
 * {@link org.axonframework.extensions.mongo.eventhandling.saga.repository.MongoSagaStore} for when it might not.
 * <p>
 * These types carry the API of the Axon Framework 4 Mongo extension, to ease migration of projects that cannot move off
 * it in one go. The departures are those Axon Framework 5 forced by removing the {@code Serializer}:
 * {@code MongoSagaStore.Builder#serializer} became {@code converter} and is now a hard requirement, since the
 * {@code XStreamSerializer} it used to default to is gone; the
 * {@link org.axonframework.extensions.mongo.eventhandling.saga.repository.SagaEntry} constructor takes a
 * {@link org.axonframework.conversion.Converter}; and {@code SagaEntry#getSaga} takes the class to convert into, which
 * the {@code Serializer} used to resolve from the type name stored alongside the saga.
 * <p>
 * The Mongo driver is an optional dependency of this module, so these types are only usable by a project that declares
 * it.
 */
@NullMarked
package org.axonframework.extensions.mongo.eventhandling.saga.repository;

import org.jspecify.annotations.NullMarked;
