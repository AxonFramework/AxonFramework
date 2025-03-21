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

package org.axonframework.common.infra;

import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * A {@link ComponentDescriptor} implementation inspired by filesystem structures. Components are represented as
 * "directories" with properties as "files" and references as "symbolic links". This creates a clear hierarchical
 * representation with explicit paths to show component relationships.
 * <p>
 * Each component is assigned a path in the virtual filesystem. References to components that have already been
 * described are represented as symbolic links, indicated with the {@code -> /path/to/component} syntax, preventing
 * infinite recursion in circular references.
 * <p>
 * Example output:
 * <pre>
 * /
 * ├── systemComponent/
 * │   ├── _type: EventProcessor
 * │   ├── name: my-processor
 * │   └── configuration/
 * │       ├── _type: ProcessorConfiguration
 * │       ├── batchSize: 100
 * │       └── processor -> /systemComponent
 * </pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class FilesystemComponentDescriptor implements ComponentDescriptor {

    private static final String ROOT_PATH = "/";
    private static final String PATH_SEPARATOR = "/";
    private static final String SYMLINK_INDICATOR = " -> ";

    private final Map<Object, String> componentPaths;
    private final Map<String, Object> properties;
    private final String currentPath;

    /**
     * Constructs a new {@code FilesystemComponentDescriptor} as the root of the hierarchy.
     */
    public FilesystemComponentDescriptor() {
        this(new IdentityHashMap<>(), ROOT_PATH);
    }

    /**
     * Private constructor used for creating nested descriptors that share the component paths map.
     *
     * @param componentPaths Map containing paths to already processed components
     * @param currentPath    The current path in the virtual filesystem
     */
    private FilesystemComponentDescriptor(Map<Object, String> componentPaths, String currentPath) {
        this.componentPaths = componentPaths;
        this.currentPath = currentPath;
        this.properties = new LinkedHashMap<>();
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Object object) {
        if (object instanceof DescribableComponent component) {
            describeComponent(name, component);
        } else {
            properties.put(name, object);
        }
    }

    private void describeComponent(String name, DescribableComponent component) {
        // Create the path for this component
        String componentPath = createPath(name);

        // Check if we've already processed this component
        if (componentPaths.containsKey(component)) {
            // Create a symbolic link to the existing component
            String existingPath = componentPaths.get(component);
            properties.put(name, new SymbolicLink(existingPath));
            return;
        }

        // Register this component before processing its properties.
        // This prevents infinite recursion with circular references.
        componentPaths.put(component, componentPath);

        // Create a nested descriptor for this component
        FilesystemComponentDescriptor childDescriptor =
                new FilesystemComponentDescriptor(componentPaths, componentPath);

        // Add type information
        childDescriptor.properties.put("_type", component.getClass().getSimpleName());

        // Let the component describe itself
        component.describeTo(childDescriptor);

        // Add the component as a child node
        properties.put(name, childDescriptor);
    }

    private String createPath(String name) {
        if (currentPath.equals(ROOT_PATH)) {
            return currentPath + name;
        }
        return currentPath + PATH_SEPARATOR + name;
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Collection<?> collection) {
        List<Object> items = new ArrayList<>();

        int index = 0;
        for (Object item : collection) {
            if (item instanceof DescribableComponent component) {
                // For components in a collection, we use indexed paths
                String itemName = name + "[" + index + "]";
                String itemPath = createPath(itemName);

                // Check if this component has already been processed
                if (componentPaths.containsKey(component)) {
                    // Create a reference to the existing component
                    String existingPath = componentPaths.get(component);
                    items.add(new SymbolicLink(existingPath));
                } else {
                    // Process a new component
                    componentPaths.put(component, itemPath);

                    FilesystemComponentDescriptor childDescriptor =
                            new FilesystemComponentDescriptor(componentPaths, itemPath);

                    childDescriptor.properties.put("_type", component.getClass().getSimpleName());
                    component.describeTo(childDescriptor);
                    items.add(childDescriptor);
                }
            } else {
                // For non-component items, just add the value
                items.add(item);
            }
            index++;
        }

        properties.put(name, items);
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Map<?, ?> map) {
        Map<String, Object> mappedItems = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();

            if (value instanceof DescribableComponent component) {
                // For components in a map, we use key-based paths
                String itemName = name + "[" + key + "]";
                String itemPath = createPath(itemName);

                // Check if this component has already been processed
                if (componentPaths.containsKey(component)) {
                    // Create a reference to the existing component
                    String existingPath = componentPaths.get(component);
                    mappedItems.put(key, new SymbolicLink(existingPath));
                } else {
                    // Process a new component
                    componentPaths.put(component, itemPath);

                    FilesystemComponentDescriptor childDescriptor =
                            new FilesystemComponentDescriptor(componentPaths, itemPath);

                    childDescriptor.properties.put("_type", component.getClass().getSimpleName());
                    component.describeTo(childDescriptor);
                    mappedItems.put(key, childDescriptor);
                }
            } else {
                // For non-component values, just add the value
                mappedItems.put(key, value);
            }
        }

        properties.put(name, mappedItems);
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull String value) {
        properties.put(name, value);
    }

    @Override
    public void describeProperty(@Nonnull String name, long value) {
        properties.put(name, value);
    }

    @Override
    public void describeProperty(@Nonnull String name, boolean value) {
        properties.put(name, value);
    }

    @Override
    public String describe() {
        return TreeRenderer.render(properties);
    }

    private record SymbolicLink(String targetPath) {

        @Override
        public String toString() {
            return SYMLINK_INDICATOR + targetPath;
        }
    }

    private static class TreeRenderer {

        /**
         * Recursively renders the properties as a filesystem-like tree structure.
         *
         * @param props The properties to render
         * @return The rendered tree as a string
         */
        private static String render(Map<String, Object> props) {
            var result = new StringBuilder();
            result.append(ROOT_PATH).append("\n");

            renderProperties(result, props, "", "");

            return result.toString();
        }

        /**
         * Recursively renders properties as a filesystem-like tree.
         *
         * @param result The StringBuilder to append the result to
         * @param props  The properties to render
         * @param indent The current indentation string
         * @param path   The current path in the tree
         */
        private static void renderProperties(
                StringBuilder result,
                Map<String, Object> props,
                String indent,
                String path) {
            var entries = new ArrayList<>(props.entrySet());

            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                var name = entry.getKey();
                var value = entry.getValue();

                var isLast = (i == entries.size() - 1);
                var connector = isLast ? "└── " : "├── ";
                var childIndent = indent + (isLast ? "    " : "│   ");

                renderProperty(result, name, value, indent, childIndent, connector, path);
            }
        }

        /**
         * Renders a single property based on its type.
         */
        private static void renderProperty(StringBuilder result, String name, Object value,
                                           String indent, String childIndent, String connector, String path) {
            switch (value) {
                case FilesystemComponentDescriptor descriptor -> renderComponentDirectory(result,
                                                                                          name,
                                                                                          descriptor,
                                                                                          indent,
                                                                                          childIndent,
                                                                                          connector,
                                                                                          path);
                case List<?> list -> renderList(result, name, list, indent, childIndent, connector, path);
                case Map<?, ?> map -> renderMap(result, name, map, indent, childIndent, connector, path);
                case SymbolicLink link -> renderSymlink(result, name, link, indent, connector);
                case null, default -> renderSimpleValue(result, name, value, indent, connector);
            }
        }

        /**
         * Renders a component as a directory.
         */
        private static void renderComponentDirectory(StringBuilder result, String name,
                                                     FilesystemComponentDescriptor descriptor,
                                                     String indent, String childIndent,
                                                     String connector, String path) {
            result.append(indent).append(connector).append(name).append("/\n");
            renderProperties(result, descriptor.properties, childIndent, path + "/" + name);
        }

        /**
         * Renders a list as a directory with numbered items.
         */
        private static void renderList(StringBuilder result, String name, List<?> list,
                                       String indent, String childIndent, String connector, String path) {
            result.append(indent).append(connector).append(name).append("/\n");

            for (int j = 0; j < list.size(); j++) {
                var item = list.get(j);
                var isLastItem = (j == list.size() - 1);
                var itemConnector = isLastItem ? "└── " : "├── ";
                var itemIndent = childIndent + (isLastItem ? "    " : "│   ");
                var indexName = "[" + j + "]";

                renderSingleEntry(result, indexName, item, childIndent, itemIndent, itemConnector, path + "/" + name);
            }
        }

        private static void renderSingleEntry(StringBuilder result, String indexName, Object item, String indent,
                                              String childIndent,
                                              String connector, String path) {
            if (item instanceof FilesystemComponentDescriptor itemDescriptor) {
                result.append(indent).append(connector).append(indexName).append("/\n");
                renderProperties(result, itemDescriptor.properties, childIndent, path + "/" + indexName);
            } else if (item instanceof SymbolicLink link) {
                result.append(indent).append(connector).append(indexName)
                      .append(SYMLINK_INDICATOR).append(link.targetPath).append("\n");
            } else {
                result.append(indent).append(connector).append(indexName)
                      .append(": ").append(formatValue(item)).append("\n");
            }
        }

        /**
         * Renders a map as a directory with named entries.
         */
        private static void renderMap(
                StringBuilder result,
                String name,
                Map<?, ?> map,
                String indent,
                String childIndent,
                String connector,
                String path) {
            result.append(indent).append(connector).append(name).append("/\n");

            var mapEntries = new ArrayList<>(map.entrySet());
            mapEntries.sort(Comparator.comparing(e -> e.getKey().toString()));
            for (int j = 0; j < mapEntries.size(); j++) {
                var mapEntry = mapEntries.get(j);
                var key = mapEntry.getKey().toString();
                var mapValue = mapEntry.getValue();
                var isLastMapEntry = (j == mapEntries.size() - 1);
                var mapConnector = isLastMapEntry ? "└── " : "├── ";
                var mapIndent = childIndent + (isLastMapEntry ? "    " : "│   ");

                renderSingleEntry(result, key, mapValue, childIndent, mapIndent, mapConnector, path + "/" + name);
            }
        }

        /**
         * Renders a symbolic link.
         */
        private static void renderSymlink(StringBuilder result, String name, SymbolicLink link, String indent,
                                          String connector) {
            result.append(indent).append(connector).append(name)
                  .append(SYMLINK_INDICATOR).append(link.targetPath).append("\n");
        }

        /**
         * Renders a simple value (string, number, boolean).
         */
        private static void renderSimpleValue(StringBuilder result, String name, Object value, String indent,
                                              String connector) {
            result.append(indent).append(connector).append(name)
                  .append(": ").append(formatValue(value)).append("\n");
        }

        /**
         * Formats a value for display in the tree structure.
         */
        private static String formatValue(Object value) {
            if (value == null) {
                return "null";
            }
            return value.toString();
        }
    }
}