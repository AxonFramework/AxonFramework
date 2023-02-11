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

package org.axonframework.migration;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class AxonJakartaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .classpath("axon-messaging", "rewrite-migrate-java"))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath()
                        .build()
                        .activateRecipes("org.axonframework.migration.UpgradeAxonFramework_4_7_Jakarta"));
    }

    @Test
    void migrateImports() {
        //language=java
        rewriteRun(
                java(
                        "package sample.axon;\n" +
                                "import org.axonframework.eventhandling.deadletter.legacyjpa.DeadLetterJpaConverter;\n" +
                                "class ATest {\n" +
                                "   DeadLetterJpaConverter converter;\n" +
                                "}",

                        "package sample.axon;\n\n" +
                                "import org.axonframework.eventhandling.deadletter.jpa.DeadLetterJpaConverter;\n\n" +
                                "class ATest {\n" +
                                "   DeadLetterJpaConverter converter;\n" +
                                "}"
                ));
    }

    @Test
    void migrateDependencies() {
        //language=xml
        rewriteRun(
                mavenProject("any-project",
                        pomXml(

                                "    <project>\n" +
                                        "        <modelVersion>4.0.0</modelVersion>\n" +
                                        "        <groupId>com.example</groupId>\n" +
                                        "        <artifactId>axon</artifactId>\n" +
                                        "        <version>1.0.0</version>\n" +
                                        "        <dependencies>\n" +
                                        "            <dependency>\n" +
                                        "                <groupId>org.axonframework</groupId>\n" +
                                        "                <artifactId>axon-configuration-jakarta</artifactId>\n" +
                                        "                <version>4.6.3</version>\n" +
                                        "            </dependency>\n" +
                                        "        </dependencies>\n" +
                                        "    </project>\n",
                                "    <project>\n" +
                                        "        <modelVersion>4.0.0</modelVersion>\n" +
                                        "        <groupId>com.example</groupId>\n" +
                                        "        <artifactId>axon</artifactId>\n" +
                                        "        <version>1.0.0</version>\n" +
                                        "        <dependencies>\n" +
                                        "            <dependency>\n" +
                                        "                <groupId>org.axonframework</groupId>\n" +
                                        "                <artifactId>axon-configuration</artifactId>\n" +
                                        "                <version>4.7.1</version>\n" +
                                        "            </dependency>\n" +
                                        "        </dependencies>\n" +
                                        "    </project>\n"
                        )));
    }

}
