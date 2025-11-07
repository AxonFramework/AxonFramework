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

package org.axonframework.common;

import java.util.UUID;

/**
 * Default IdentifierFactory implementation that uses generates random {@code java.util.UUID} based identifiers.
 * Although the performance of this strategy is not the best out there, it has native supported on all JVMs.
 * <p/>
 * This implementations selects a random identifier out of about 3 x 10<sup>38</sup> possible values, making the chance
 * to get a duplicate incredibly small.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class DefaultIdentifierFactory extends IdentifierFactory {

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation creates identifiers based on pseudo-random UUIDs.
     */
    @Override
    public String generateIdentifier() {
        return UUID.randomUUID().toString();
    }
}
