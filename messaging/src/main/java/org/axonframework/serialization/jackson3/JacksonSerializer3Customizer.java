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

package org.axonframework.serialization.jackson3;

import tools.jackson.databind.json.JsonMapper;

import java.util.function.Consumer;

/**
 * Non-generic interface for a consumer that accepts a {@link JsonMapper.Builder}
 * for customizing {@link JacksonSerializer3}.
 */
public interface JacksonSerializer3Customizer extends Consumer<JsonMapper.Builder> {}
