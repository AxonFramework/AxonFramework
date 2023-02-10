package org.axonframework.migration;


import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

public class AxonJakartaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .classpath("axon-messaging"))
                .recipe("/META-INF/rewrite/axon-jakarta.yml", "org.axonframework.axon.UpgradeAxonFramework_4_7_Jakarta");
    }

    @Test
    void migrateImports() {
        //language=java
        rewriteRun(
                java(
                        "package sample.axon;\n" +
                                "import org.axonframework.eventhandling.deadletter.jpa.DeadLetterJpaConverter;\n" +
                                "class ATest {\n" +
                                "DeadLetterJpaConverter converter;\n" +
                                "}",

                        "package sample.axon;\n" +
                                "import org.axonframework.eventhandling.deadletter.legacyjpa.DeadLetterJpaConverter;\n" +
                                "class ATest {\n" +
                                "DeadLetterJpaConverter converter;\n" +
                                "}"
                ));
    }

    @Test
    void migrateDependencies() {
        //language=xml
        rewriteRun(
                pomXml(

                        "    <project>\n" +
                        "        <modelVersion>4.0.0</modelVersion>\n" +
                        "        <groupId>com.example</groupId>\n" +
                        "        <artifactId>axon</artifactId>\n" +
                        "        <version>1.0.0</version>\n" +
                        "        <build>\n" +
                        "            <plugins>\n" +
                        "                <plugin>\n" +
                        "                    <groupId>org.axonframework</groupId>\n" +
                        "                    <artifactId>axon-config-jakarta</artifactId>\n" +
                        "                    <version>4.6.0</version>\n" +
                        "                </plugin>\n" +
                        "            </plugins>\n" +
                        "        </build>\n" +
                        "    </project>\n",
                        "    <project>\n" +
                        "        <modelVersion>4.0.0</modelVersion>\n" +
                        "        <groupId>com.example</groupId>\n" +
                        "        <artifactId>axon</artifactId>\n" +
                        "        <version>1.0.0</version>\n" +
                        "        <build>\n" +
                        "            <plugins>\n" +
                        "                    <groupId>org.axonframework</groupId>\n" +
                        "                    <artifactId>axon-config-jakarta</artifactId>\n" +
                        "                    <version>4.7.1</version>\n" +
                        "                </plugin>\n" +
                        "            </plugins>\n" +
                        "        </build>\n" +
                        "    </project>\n"
                ));
    }

}
