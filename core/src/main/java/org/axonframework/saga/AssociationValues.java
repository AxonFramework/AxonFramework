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

package org.axonframework.saga;

import java.util.Set;

/**
 * Interface describing a collection of {@link AssociationValue Association Values} for a single {@link Saga} instance.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface AssociationValues extends Set<AssociationValue> {

    /**
     * Registers a listener that is notified when AssociationValue instances are wither added or removed from this
     * collection.
     *
     * @param changeListener The listener to register
     */
    void addChangeListener(ChangeListener changeListener);

    /**
     * Removes the registered changeListener.
     *
     * @param changeListener The listener to remove
     */
    void removeChangeListener(ChangeListener changeListener);

    /**
     * Interface describing instances that listen for modification in an AssociationValues instance. Methods on this
     * interface are invoked immediately <em>after</em> the event has occurred, and before the changes have been
     * committed to a repository.
     */
    interface ChangeListener {

        /**
         * Invoked when an AssociationValue has been added to the collection.
         *
         * @param newAssociationValue The AssociationValue that has been added
         */
        void onAssociationValueAdded(AssociationValue newAssociationValue);

        /**
         * Invoked when an AssociationValue is removed from the collection.
         *
         * @param associationValue The AssociationValue that has been removed
         */
        void onAssociationValueRemoved(AssociationValue associationValue);
    }
}
