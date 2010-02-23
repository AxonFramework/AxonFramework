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
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jettro Coenradie
 */
@Controller
public class LoadPropertiesController {
    private static Logger logger = LoggerFactory.getLogger(LoadPropertiesController.class);

    @RequestMapping("/config.properties")
    public String handleConfigProperties(HttpServletRequest request, ModelMap model) {
        logger.debug("Requested the properties url {}",request.getRequestURI());
        
        Map<String,String> params = new HashMap<String,String>();
        params.put("host",request.getServerName());
        params.put("port",String.valueOf(request.getServerPort()));
        String contextRoot = request.getContextPath();
        if (contextRoot.length() > 0) {
            contextRoot = contextRoot.substring(1);
        }
        params.put("context-root", contextRoot);

        model.addAttribute("exposedParams",params);
        return "propertiesView";
    }
}
