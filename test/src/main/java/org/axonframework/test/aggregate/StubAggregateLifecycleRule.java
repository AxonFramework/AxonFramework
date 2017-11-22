/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.test.aggregate;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Implementation of StubAggregateLifecycle that can be used as an {@link org.junit.Rule} annotated method or field
 * in a test class. In that case, the JUnit lifecycle will automatically register and unregister the
 * StubAggregateLifecycle.
 *
 * Usage example:
 * <pre>
 *     &#064;Rule
 *     public StubAggregateLifecycleRule lifecycle = new StubAggregateLifecycleRule();
 *
 *     &#064;Test
 *     public void testMethod() {
 *         ... perform tests ...
 *
 *         // get applied events from lifecycle to validate some more
 *         lifecycle.getAppliedEvents();
 *     }
 * </pre>
 */
public class StubAggregateLifecycleRule extends StubAggregateLifecycle implements TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                activate();
                try {
                    base.evaluate();
                } finally {
                    close();
                }
            }
        };
    }

}
