/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventhandling;

/**
 * Interface making a token that wraps another token. As certain implementations may depend on specific token types,
 * Tokens that wrap another must provide a means to retrieve the original token.
 *
 * @author Allard Buijze
 * @since 3.2
 */
public interface WrappedToken {

    /**
     * Returns the token representing the current position in the stream.
     *
     * @return the token representing the current position in the stream
     */
    TrackingToken unwrap();
}
