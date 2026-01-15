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

package org.axonframework.update.configuration;

import org.axonframework.common.annotation.Internal;

/**
 * Default implementation of the {@link UsagePropertyProvider} interface. This implementation provides default values
 * for the properties used in the Axon Framework usage API. It is used when no other provider has a value.
 * <p>
 * As there is no dependent state and holds default values, this class is implemented as a singleton. Retrieve the
 * instance using {@link #INSTANCE}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class DefaultUsagePropertyProvider implements UsagePropertyProvider {

    /**
     * Singleton instance of the {@link DefaultUsagePropertyProvider}.
     */
    public static DefaultUsagePropertyProvider INSTANCE = new DefaultUsagePropertyProvider();

    @Override
    public Boolean getDisabled() {
        return false;
    }

    @Override
    public String getUrl() {
        return "https://get.axoniq.io/updates/framework";
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    private DefaultUsagePropertyProvider() {
        // Private constructor to prevent instantiation
    }
}
