/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.examples.addressbook.vaadin.search;

import java.io.Serializable;

/**
 * @author Jettro Coenradie
 */
public class SearchFilter implements Serializable {

    private final String term;
    private final Object propertyId;
    private String searchName;

    public SearchFilter(Object propertyId, String searchTerm, String name) {
        this.propertyId = propertyId;
        this.term = searchTerm;
        this.searchName = name;
    }

    public Object getPropertyId() {
        return propertyId;
    }

    public String getSearchName() {
        return searchName;
    }

    public String getTerm() {
        return term;
    }
}