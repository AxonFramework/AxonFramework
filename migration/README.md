# Migration
Module containing [OpenRewrite](https://docs.openrewrite.org/) recipes for migrating Axon Framework applications.

## Migrate to Axon Framework 4.x Javax
### Maven
```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.axonframework:axon-migration:LATEST \
  -DactiveRecipes=org.axonframework.migration.UpgradeAxonFramework_4_Javax
```

#### Gradle
Requires a custom [Gradle init script](https://docs.openrewrite.org/running-recipes/running-rewrite-on-a-gradle-project-without-modifying-the-build).

## Migrate to Axon Framework 4.x Jakarta
### Maven
```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.axonframework:axon-migration:LATEST \
  -DactiveRecipes=org.axonframework.migration.UpgradeAxonFramework_4_Jakarta
```

#### Gradle
Requires a custom [Gradle init script](https://docs.openrewrite.org/running-recipes/running-rewrite-on-a-gradle-project-without-modifying-the-build).


## Migrate to Axon Framework 4.x Jakarta and Spring Boot 3.2
### Maven
```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:LATEST,org.axonframework:axon-migration:LATEST \
  -DactiveRecipes=org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_2,org.axonframework.migration.UpgradeAxonFramework_4_Jakarta
```

#### Gradle
Requires a custom [Gradle init script](https://docs.openrewrite.org/running-recipes/running-rewrite-on-a-gradle-project-without-modifying-the-build).