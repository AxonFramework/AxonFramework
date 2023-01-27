/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.test.aggregate;

import org.junit.jupiter.api.extension.*;

/**
 * Implementation of {@link StubAggregateLifecycle} that can be used as an {@link org.junit.jupiter.api.extension.RegisterExtension}
 * annotated method or field in a test class. In that case, the JUnit lifecycle will automatically register and
 * deregister the {@code StubAggregateLifecycle}.
 * <p>
 * Usage example:
 * <pre>
 * &#064;RegisterExtension
 * public StubAggregateLifecycleExtension lifecycle = new StubAggregateLifecycleExtension();
 *
 * &#064;Test
 * public void testMethod() {
 *     ... perform tests ...
 *
 *     // get applied events from lifecycle to validate some more
 *     lifecycle.getAppliedEvents();
 * }
 * </pre>
 */
public class StubAggregateLifecycleExtension extends StubAggregateLifecycle
        implements AfterEachCallback, BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        activate();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        close();
    }
}
