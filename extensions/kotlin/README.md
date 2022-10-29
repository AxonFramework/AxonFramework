# Axon Framework - Kotlin Extension
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.axonframework.extensions.kotlin/axon-kotlin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.axonframework.extensions.kotlin/axon-kotlin)
![Build Status](https://github.com/AxonFramework/extension-kotlin/workflows/Kotlin%20Extension/badge.svg?branch=master)
[![SonarCloud Status](https://sonarcloud.io/api/project_badges/measure?project=AxonFramework_extension-kotlin&metric=alert_status)](https://sonarcloud.io/dashboard?id=AxonFramework_extension-kotlin)
[![Open Source Helpers](https://www.codetriage.com/axonframework/extension-kotlin/badges/users.svg)](https://www.codetriage.com/axonframework/extension-kotlin)

_Note:_ This extension is still in an experimental stage.

Axon Framework is a framework for building evolutionary, event-driven microservice systems,
 based on the principles of Domain Driven Design, Command-Query Responsibility Segregation (CQRS) and Event Sourcing.

As such it provides you the necessary building blocks to follow these principles.
Building blocks like Aggregate factories and Repositories, Command, Event and Query Buses and an Event Store.
The framework provides sensible defaults for all of these components out of the box.

This set up helps you create a well-structured application without having to bother with the infrastructure.
The main focus can thus become your business functionality.

This repository provides an extension to the Axon Framework: Kotlin. It provides functionality to leverage Kotlin features to be used with Axon Framework.

For more information on anything Axon, please visit our website, [http://axoniq.io](http://axoniq.io).

## Getting started

### Dependencies

For the Kotlin extension itself you can get the version from the [axon-bom use](https://github.com/AxonFramework/axon-bom) or use the following coordinates:

**Maven**

```
<dependency>
    <groupId>org.axonframework.extensions.kotlin</groupId>
    <artifactId>axon-kotlin</artifactId>
    <version>4.6.0</version>
</dependency>
```

**Gradle**

```
implementation("org.axonframework.extensions.kotlin:axon-kotlin:4.6.0")
```

For the Kotlin testing extension itself please use the following coordinates:

**Maven**

```
<dependency>
    <groupId>org.axonframework.extensions.kotlin</groupId>
    <artifactId>axon-kotlin-test</artifactId>
    <version>4.6.0</version>
</dependency>
```

**Gradle**

```
implementation("org.axonframework.extensions.kotlin:axon-kotlin-test:4.6.0")
```


## Receiving help

Are you having trouble using the extension?
We'd like to help you out the best we can!
There are a couple of things to consider when you're traversing anything Axon:

* Checking the [reference guide](https://docs.axoniq.io/reference-guide/extensions/kotlin) should be your first stop,
 as the majority of possible scenarios you might encounter when using Axon should be covered there.
* If the Reference Guide does not cover a specific topic you would've expected,
 we'd appreciate if you could file an [issue](https://github.com/AxonIQ/reference-guide/issues) about it for us.
* There is a [forum](https://discuss.axoniq.io/) to support you in the case the reference guide did not sufficiently answer your question.
Axon Framework and Server developers will help out on a best effort basis.
Know that any support from contributors on posted question is very much appreciated on the forum.
* Next to the forum we also monitor Stack Overflow for any questions which are tagged with `axon`.

## Feature requests and issue reporting

We use GitHub's [issue tracking system](https://github.com/AxonFramework/extension-kotlin/issues) for new feature
request, extension enhancements and bugs.
Prior to filing an issue, please verify that it's not already reported by someone else.

When filing bugs:
* A description of your setup and what's happening helps us figuring out what the issue might be
* Do not forget to provide the version you're using
* If possible, share a stack trace, using the Markdown semantic ```

When filing features:
* A description of the envisioned addition or enhancement should be provided
* (Pseudo-)Code snippets showing what it might look like help us understand your suggestion better
* If you have any thoughts on where to plug this into the framework, that would be very helpful too
* Lastly, we value contributions to the framework highly. So please provide a Pull Request as well!

## Building the extension

If you want to build the extension locally, you need to check it out from GiHub and run the following command:

    ./mvnw clean install

### Producing JavaDocs and Sources archive

Please execute the following command line if you are interested in producing KDoc and Source archives:

    ./mvnw clean install -Pjavadoc-and-sources


---
