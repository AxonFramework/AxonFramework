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

package org.axonframework.updates.configuration;

class DummyProvider implements UsagePropertyProvider {

    private final Boolean disabled;
    private final String url;
    private final int priority;

    DummyProvider(Boolean disabled, String url, int priority) {
        this.disabled = disabled;
        this.url = url;
        this.priority = priority;
    }

    @Override
    public Boolean getDisabled() {
        return disabled;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public int priority() {
        return priority;
    }
}
