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

package org.axonframework.examples.addressbook.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.view.AbstractView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;

/**
 * @author Jettro Coenradie
 */
public class PropertyView extends AbstractView {
    private static Logger logger = LoggerFactory.getLogger(PropertyView.class);

    public PropertyView() {
        setContentType("text/plain");
    }

    protected void renderMergedOutputModel(Map model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        logger.debug("Render view for requested resource {}",request.getRequestURI());
        Map<String,String> exposedParams = (Map<String,String>)model.get("exposedParams");

        Set<Map.Entry<String,String>> entries = exposedParams.entrySet();

        response.setContentType(getContentType());
        for(Map.Entry<String,String> entry : entries) {
            StringBuilder lineBuilder = new StringBuilder();
            lineBuilder.append(entry.getKey());
            lineBuilder.append("=");
            lineBuilder.append(entry.getValue());
            response.getWriter().println(lineBuilder.toString());
        }
    }
    
}
