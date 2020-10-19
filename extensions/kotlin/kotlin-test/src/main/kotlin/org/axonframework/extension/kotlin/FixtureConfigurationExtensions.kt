/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.extension.kotlin

import org.axonframework.test.aggregate.FixtureConfiguration

/**
 * Indicates that a field with given {@code fieldName}, which is declared in given {@code declaringClass}
 * is ignored when performing deep equality checks.
 *
 * @param [T] type of fixture target.
 * @param [F] filed type.
 * @param fieldName The name of the field
 * @return the current FixtureConfiguration, for fluent interfacing
 */
inline fun <T : Any, reified F : Any> FixtureConfiguration<T>.registerIgnoredField(fieldName: String): FixtureConfiguration<T> =
        this.registerIgnoredField(F::class.java, fieldName)