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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AxonJavaxTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .classpath("axon-messaging"))
                .recipe("/META-INF/rewrite/axon-javax-47.yml", "org.axonframework.migration.UpgradeAxonFramework_4_7_Javax");
    }

    @Test
    void migrateImports() {
        //language=java
        rewriteRun(
                java(
                        "package sample.axon;\n" +
                                "import org.axonframework.eventhandling.deadletter.jpa.DeadLetterJpaConverter;\n" +
                                "class ATest {\n" +
                                "   DeadLetterJpaConverter converter;\n" +
                                "}",

                        "package sample.axon;\n\n" +
                                "import org.axonframework.eventhandling.deadletter.legacyjpa.DeadLetterJpaConverter;\n\n" +
                                "class ATest {\n" +
                                "   DeadLetterJpaConverter converter;\n" +
                                "}"
                ));
    }

}
