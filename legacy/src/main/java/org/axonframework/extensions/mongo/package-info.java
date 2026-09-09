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
 * Access to the Mongo collections the components in this module read and write, through
 * {@link org.axonframework.extensions.mongo.MongoTemplate} and its
 * {@link org.axonframework.extensions.mongo.DefaultMongoTemplate default implementation}.
 * <p>
 * These types carry the API of the Axon Framework 4 Mongo extension, to ease migration of projects that cannot move off
 * it in one go. The one departure is scope: the extension's {@code MongoTemplate} also handed out the domain event,
 * snapshot, tracking token and dead letter collections, and only the saga collection has a component here to use it.
 * <p>
 * The Mongo driver is an optional dependency of this module, so these types are only usable by a project that declares
 * it.
 */
@NullMarked
package org.axonframework.extensions.mongo;

import org.jspecify.annotations.NullMarked;
