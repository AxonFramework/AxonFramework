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

import java.util.List;

/**
 * Stub Domain Event, used for testing purposes.
 *
 * @author Steven van Beelen
 */
public class ThirdStubEvent {

    private final String name;
    private final Integer number;
    private final List<Boolean> truths;

    // No-arg constructor required for JacksonSerializer
    @SuppressWarnings("unused")
    private ThirdStubEvent() {
        name = null;
        number = null;
        truths = null;
    }

    @SuppressWarnings("unused")
    public ThirdStubEvent(String name, Integer number, List<Boolean> truths) {
        this.name = name;
        this.number = number;
        this.truths = truths;
    }

    public String getName() {
        return name;
    }

    public Integer getNumber() {
        return number;
    }

    public List<Boolean> getTruths() {
        return truths;
    }
}
