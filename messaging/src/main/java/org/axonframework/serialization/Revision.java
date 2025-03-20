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

package org.axonframework.serialization;

import java.lang.annotation.*;

/**
 * Annotation that attaches revision information to a serializable object. The revision identifiers is used by
 * upcasters to decide whether they need to process a certain serialized event. Generally, the revision identifier
 * needs to be modified (increased) each time the structure of an event has been changed in an incompatible manner.
 * <p/>
 * Although revision identifiers are inherited, you are strictly advised to only annotate the actual implementation
 * classes used. This will make it easier to keep the necessary upcasters up-to-date.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface Revision {

    /**
     * The revision identifier for this object.
     */
    String value();
}
