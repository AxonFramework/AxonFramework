/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.messaging.retry;

import org.axonframework.common.AxonNonTransientException;

/**
 * An Axon-specific {@link java.util.function.Predicate}, used to check the non-transiency of a failure comparing it against concrete classes.
 * <p/>
 * This implementation uses only {@link AxonNonTransientException} class for comparisons.
 * <p/>
 * This {@code Predicate} acts as the default for {@link RetryScheduler} instances.
 *
 * @author Damir Murat
 * @since 4.6.0
 */
public class AxonNonTransientExceptionClassesPredicate extends NonTransientExceptionClassesPredicate {
    public AxonNonTransientExceptionClassesPredicate() {
        super(AxonNonTransientException.class);
    }
}
