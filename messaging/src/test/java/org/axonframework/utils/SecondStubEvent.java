/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.utils;

/**
 * Stub Domain Event, used for testing purposes.
 *
 * @author Steven van Beelen
 */
public class SecondStubEvent {

    private final String name;
    private final Integer number;

    // No-arg constructor required for JacksonSerializer
    @SuppressWarnings("unused")
    private SecondStubEvent() {
        name = null;
        number = null;
    }

    public SecondStubEvent(String name, Integer number) {
        this.name = name;
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public Integer getNumber() {
        return number;
    }
}
