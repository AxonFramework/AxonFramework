package org.axonframework.migration;


import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

public class AxonJavaxTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .classpath("axon-messaging"))
                .recipe("/META-INF/rewrite/axon-javax.yml", "org.axonframework.axon.UpgradeAxonFramework_4_7_Javax");
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
