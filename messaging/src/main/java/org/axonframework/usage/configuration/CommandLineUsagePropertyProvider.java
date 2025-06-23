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

package org.axonframework.usage.configuration;

/**
 * A {@link UsagePropertyProvider} implementation that reads the usage properties from the command line system
 * properties. This is the highest priority provider, meaning any defined property will override the others.
 */
public class CommandLineUsagePropertyProvider implements UsagePropertyProvider {

    @Override
    public Boolean getDisabled() {
        String property = System.getProperty("axoniq.update-checker.disabled", null);
        if(property != null) {
            return Boolean.parseBoolean(property);
        }
        return null;
    }

    @Override
    public String getUrl() {
        return System.getProperty("axoniq.usage.url", null);
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
