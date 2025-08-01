/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.updates;

import jakarta.annotation.Nonnull;
import org.axonframework.updates.api.UpdateCheckRequest;
import org.axonframework.updates.api.UpdateCheckResponse;

/**
 * Interface for reporting the response of the update checker to the user. Implementations of this interface should
 * handle how the response is communicated to the user.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface UpdateCheckerReporter {

    /**
     * Reports the given {@code response} of the update checker to the user.
     *
     * @param request  The request that was made to the update checker.
     * @param response The response to report.
     */
    void report(@Nonnull UpdateCheckRequest request, @Nonnull UpdateCheckResponse response);
}
