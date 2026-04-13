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

/**
 * Abstract test suite for integration tests that always require a running Axon Server. Extends
 * {@link AbstractIntegrationTest} and pins the backend to {@link AxonServerEventStorageEngineProvider} via
 * {@link TestEventStorageEngine}.
 * <p>
 * Most test classes should extend {@link AbstractStudentIT} or
 * {@link org.axonframework.integrationtests.testsuite.administration.AbstractAdministrationIT} (which extend
 * {@link AbstractIntegrationTest} directly and use InMemory by default). Extend this class only for tests that
 * specifically require Axon Server behavior (e.g., distributed command routing, Axon Server-specific queries).
 *
 * @author Mitchell Herrijgers
 */
@TestEventStorageEngine(AxonServerEventStorageEngineProvider.class)
public abstract class AbstractAxonServerIT extends AbstractIntegrationTest {

}
