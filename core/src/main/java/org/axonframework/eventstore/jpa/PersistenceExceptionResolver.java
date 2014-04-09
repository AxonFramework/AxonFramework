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

package org.axonframework.eventstore.jpa;

/**
 * The PersistenceExceptionResolver is used to find out if an exception is caused by  duplicate keys.
 *
 * @author Martin Tilma
 * @since 0.7
 * @deprecated Implement {@link org.axonframework.common.jdbc.PersistenceExceptionResolver} instead.
 */
@Deprecated
public interface PersistenceExceptionResolver extends org.axonframework.common.jdbc.PersistenceExceptionResolver {

}
