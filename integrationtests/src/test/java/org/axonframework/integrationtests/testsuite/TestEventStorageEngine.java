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

package org.axonframework.integrationtests.testsuite;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects the {@link EventStorageEngineProvider} to use for integration tests. Applied on
 * {@link AbstractIntegrationTest} with no value (sentinel default), and can be overridden on any subclass to pin a
 * specific backend.
 * <p>
 * Resolution order applied by {@link EventStorageEngineExtension}:
 * <ol>
 *   <li>An explicit {@code value()} on the annotation — overrides everything including the system property.</li>
 *   <li>System property {@code axon.test.storage-engine-provider} — used when no explicit value is set.</li>
 *   <li>Default: {@link InMemoryEventStorageEngineProvider} — no Docker required.</li>
 * </ol>
 * <p>
 * {@code @Inherited} ensures that all subclasses automatically inherit the provider declared on their nearest annotated
 * superclass, so concrete test classes need no annotation unless they explicitly want a different backend.
 *
 * @see EventStorageEngineProvider
 * @see EventStorageEngineExtension
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface TestEventStorageEngine {

    /**
     * the provider class to use. When set to the default sentinel value ({@link EventStorageEngineProvider}{@code
     * .class}), the extension falls back to the {@code axon.test.storage-engine-provider} system property, then to
     * {@link InMemoryEventStorageEngineProvider}. When explicitly set to any other class, it overrides the system
     * property.
     */
    Class<? extends EventStorageEngineProvider> value() default EventStorageEngineProvider.class;
}
