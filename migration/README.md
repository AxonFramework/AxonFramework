# Migration

Module containing [OpenRewrite](https://docs.openrewrite.org/) recipes for migrating Axon Framework applications.

## Migrate to Axon Framework 4.7 Javax

### Maven

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.axonframework:axon-migration:LATEST \
  -DactiveRecipes=org.axonframework.axon.UpgradeAxonFramework_4_7_Javax
```

#### Gradle

Requires a custom [Gradle init script](https://docs.openrewrite.org/running-recipes/running-rewrite-on-a-gradle-project-without-modifying-the-build).

## Migrate to Axon Framework 4.7 Jakarta

### Maven
```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.axonframework:axon-migration:4.7.2 \
  -DactiveRecipes=org.axonframework.axon.UpgradeAxonFramework_4_7_Jakarta
```

#### Gradle

Requires a custom [Gradle init script](https://docs.openrewrite.org/running-recipes/running-rewrite-on-a-gradle-project-without-modifying-the-build).
