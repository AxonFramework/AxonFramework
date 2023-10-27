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

package org.axonframework.serialization;

/**
 * Revision Resolver implementation that checks for the presence of an {@link Revision @Revision} annotation. The value
 * of that annotation is returns as the revision of the payload it annotates. Note that {@code @Revision} is an
 * inherited annotation, meaning that subclasses of annotated classes inherit the revision of their parent.
 * <p/>
 * This implementation returns {@code null} for objects that do not have a {@code @Revision} annotation.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AnnotationRevisionResolver implements RevisionResolver {

    @Override
    public String revisionOf(Class<?> payloadType) {
        Revision revision = payloadType.getAnnotation(Revision.class);
        return revision != null ? revision.value() : null;
    }
}
