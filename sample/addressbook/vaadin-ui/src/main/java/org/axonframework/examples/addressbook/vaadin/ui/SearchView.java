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

package org.axonframework.examples.addressbook.vaadin.ui;

import com.vaadin.ui.*;
import org.axonframework.examples.addressbook.vaadin.data.ContactContainer;

/**
 * @author Jettro Coenradie
 */
public class SearchView extends Panel {
    private TextField tf;
    private NativeSelect fieldToSearch;
    private CheckBox saveSearch;
    private TextField searchName;

    public SearchView() {
        setCaption("Search for contacts");
        setSizeFull();

        FormLayout formLayout = new FormLayout();
        setContent(formLayout);

        tf = new TextField("Search term");
        fieldToSearch = new NativeSelect("Field to search");
        saveSearch = new CheckBox("Save search");
        searchName = new TextField("Search name");
        Button search = new Button("Search");
        search.addListener(new Button.ClickListener() {
            public void buttonClick(Button.ClickEvent event) {
                performSearch();
            }
        });
        for (int i = 0; i < ContactContainer.NATURAL_COL_ORDER.length; i++) {
            fieldToSearch.addItem(ContactContainer.NATURAL_COL_ORDER[i]);
            fieldToSearch.setItemCaption(ContactContainer.NATURAL_COL_ORDER[i],
                    ContactContainer.COL_HEADERS_ENGLISH[i]);
        }
        fieldToSearch.setValue("name");
        fieldToSearch.setNullSelectionAllowed(false);
        saveSearch.setValue(true);
        addComponent(tf);
        addComponent(fieldToSearch);
        addComponent(saveSearch);
        addComponent(searchName);
        addComponent(search);

        saveSearch.setImmediate(true);
        saveSearch.addListener(new Button.ClickListener() {
            public void buttonClick(Button.ClickEvent event) {
                searchName.setVisible(event.getButton().booleanValue());
            }
        });
    }

    private void performSearch() {
//        String searchTerm = (String) tf.getValue();
//         SearchFilter searchFilter = new SearchFilter(fieldToSearch.getValue(),
//                 searchTerm, (String) searchName.getValue());
//         if (saveSearch.booleanValue()) {
//             app.saveSearch(searchFilter);
//         }
//         app.search(searchFilter);
    }
}
