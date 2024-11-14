<p align="center">
  <a href="https://www.axoniq.io/products/axon-framework">
    <img src="https://www.axoniq.io/hubfs/axon-framework.svg" alt="Axon Framework logo" width="200" height="200">
  </a>
</p>

<h1 align="center">Axon Framework</h1>

<p align="center">
  Build modern event-driven systems with AxonIQ technology.
  <br>
  <a href="https://www.axoniq.io/products/axon-framework"><strong>Product Description »</strong></a>
  <br>
  <br>
  <a href="https://github.com/AxonIQ/code-samples">Code Samples Repo</a>
  ·
  <a href="https://developer.axoniq.io/axon-framework/overview">Technical Overview</a>
  ·
  <a href="https://github.com/AxonFramework/AxonFramework/issues">Feature / Bug Request</a>

</p>




# Axon Framework
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.axonframework/axon/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.axonframework/axon)
![Build Status](https://github.com/AxonFramework/AxonFramework/workflows/Axon%20Framework/badge.svg?branch=master)
[![SonarCloud Status](https://sonarcloud.io/api/project_badges/measure?project=AxonFramework_AxonFramework&metric=alert_status)](https://sonarcloud.io/dashboard?id=AxonFramework_AxonFramework)
[![Gurubase](https://img.shields.io/badge/Gurubase-Ask%20Axon%20Framework%20Guru-006BFF)](https://gurubase.io/g/axon-framework)

Axon Framework is a framework for building evolutionary, event-driven microservice systems based on the principles of Domain-Driven Design (DDD), Command-Query Responsibility Separation (CQRS), and Event Sourcing.

<img src="https://library.axoniq.io/axoniq-console-getting-started/main/ac-monitor-axon-framework-applications/_images/ac-message-dependency-diagram.png" alt="Bootstrap logo">

Axon Framework provides you with the necessary building blocks to follow these principles.
Examples of building blocks are aggregate design handles, aggregate repositories, command buses, saga design handles, event stores, query buses, and more.
The framework provides sensible defaults for all of these components out of the box.

The messaging support for commands, events, and queries is at the core of these building blocks. 
It is the messaging basics that enable an evolutionary approach towards microservices through the [location transparency](https://en.wikipedia.org/wiki/Location_transparency) they provide.

Axon will also assist in distributing applications to support scalability or fault tolerance, for example.
The most accessible and quick road forward would be to use [Axon Server](https://developer.axoniq.io/axon-server/overview) to seamlessly adjust message buses to distributed implementations.
Axon Server provides a distributed command bus, event bus, query bus, and an efficient event store implementation for scalable event sourcing.
Additionally, the [Axon Framework organization](https://github.com/AxonFramework) has several extensions that can help in this space.

All this helps to create a well-structured application without worrying about the infrastructure.
Hence, your focus can shift from non-functional requirements to your business functionality.

For more information on anything Axon, please visit our website, [http://axoniq.io](http://axoniq.io).

## Getting started

Numerous resources can help you on your journey in using Axon Framework.
A good starting point is [AxonIQ Developer Portal](https://developer.axoniq.io/), which provides links to resources like blogs, videos, and descriptions.

Furthermore, below are several other helpful resources:
* The [quickstart page](https://docs.axoniq.io/reference-guide/getting-started/quick-start) of the documentation provides a simplified entry point into the framework with the [quickstart project](https://download.axoniq.io/quickstart/AxonQuickStart.zip).
* We have our very own [academy](https://academy.axoniq.io/)! 
  The introductory courses are free, followed by more in-depth (paid) courses.
* When ready, you can quickly and easily start your very own Axon Framework based application at https://start.axoniq.io/. 
  Note that this solution is only feasible if you want to stick to the Spring ecosphere.
* The [reference guide](https://docs.axoniq.io) explains all of the components maintained within Axon Framework's products.
* If the guide doesn't help, our [forum](https://discuss.axoniq.io/) provides a place to ask questions you have during development.
* The [hotel demo](https://github.com/AxonIQ/hotel-demo) shows a fleshed-out example of using Axon Framework.
* The [code samples repository](https://github.com/AxonIQ/code-samples) contains more in-depth samples you can benefit from.

## Receiving help

Are you having trouble using any of our libraries or products?
Know that we want to help you out the best we can!
There are a couple of things to consider when you're traversing anything Axon:

* Checking the [reference guide](https://docs.axoniq.io) should be your first stop.
* When the reference guide does not cover your predicament, we would greatly appreciate it if you could file an [issue](https://github.com/AxonIQ/reference-guide/issues) for it.
* Our [forum](https://discuss.axoniq.io/) provides a space to communicate with the Axon community to help you out. 
  AxonIQ developers will help you out on a best-effort basis. 
  And if you know how to help someone else, we greatly appreciate your contributions!
* We also monitor Stack Overflow for any question tagged with [**axon**](https://stackoverflow.com/questions/tagged/axon). 
  Similarly to the forum, AxonIQ developers help out on a best-effort basis.

## Feature requests and issue reporting

We use GitHub's [issue tracking system](https://github.com/AxonFramework/AxonFramework/issues)) for new feature requests, framework enhancements, and bugs.
Before filing an issue, please verify that it's not already reported by someone else. 
Furthermore, make sure you are adding the issue to the correct repository!

When filing bugs:
* A description of your setup and what's happening helps us figure out what the issue might be.
* Do not forget to provide the versions of the Axon products you're using, as well as the language and version.
* If possible, share a stack trace. 
  Please use Markdown semantics by starting and ending the trace with three backticks (```).

When filing a feature or enhancement:
* Please provide a description of the feature or enhancement at hand. 
  Adding why you think this would be beneficial is also a great help to us.
* (Pseudo-)Code snippets showing what it might look like will help us understand your suggestion better.
  Similarly as with bugs, please use Markdown semantics for code snippets, starting and ending with three backticks (```).
* If you have any thoughts on where to plug this into the framework, that would be very helpful too.
* Lastly, we value contributions to the framework highly. 
  So please provide a Pull Request as well!
