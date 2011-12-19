/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.management;

/**
 * Interface describing the criteria that DomainEvent entries must match against. These criteria can be combined with
 * other criteria using AND and OR operators.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface Criteria {

    /**
     * Returns a criteria instance where both <code>this</code> and given <code>criteria</code> must match.
     *
     * @param criteria The criteria that must match
     * @return a criteria instance that matches if both <code>this</code> and <code>criteria</code> match
     */
    Criteria and(Criteria criteria);

    /**
     * Returns a criteria instance where either <code>this</code> or the given <code>criteria</code> must match.
     *
     * @param criteria The criteria that must match if <code>this</code> doesn't match
     * @return a criteria instance that matches if <code>this</code> or the given <code>criteria</code> match
     */
    Criteria or(Criteria criteria);
}
