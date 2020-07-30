# Axon Framework 
[![Build Status](https://travis-ci.org/AxonFramework/AxonFramework.svg?branch=master)](https://travis-ci.org/AxonFramework/AxonFramework)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.axonframework/axon/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.axonframework/axon)

Axon Framework is a framework for building evolutionary, event-driven microservice systems,
 based on the principles of Domain Driven Design, Command-Query Responsibility Segregation (CQRS) and Event Sourcing.

As such it provides you the necessary building blocks to follow these principles. 
Building blocks like Aggregate factories and Repositories, Command, Event and Query Buses, and an Event Store.
The framework provides sensible defaults for all of these components out of the box.

This set up helps you create a well-structured application without having to bother with the infrastructure.
The main focus can thus become your business functionality.
  
For more information on anything Axon, please visit our website, [http://axoniq.io](http://axoniq.io).

## Getting started

The quickstart [page](https://docs.axoniq.io/reference-guide/quick-start) of the 
[reference guide](https://docs.axoniq.io) provides a starting point for using Axon.

## Receiving help

Are you having trouble using the framework (or Axon Server)? 
We'd like to help you out the best we can!
There are a couple of things to consider when you're traversing anything Axon:

* Checking the [reference guide](https://docs.axoniq.io) should be your first stop,
 as the majority of possible scenarios you might encounter when using Axon should be covered there.
* If the Reference Guide does not cover a specific topic you would've expected,
 we'd appreciate if you could file an [issue](https://github.com/AxonIQ/reference-guide/issues) about it for us. 
* There is a [public mailing list](https://groups.google.com/forum/#!forum/axonframework) to support you in the case 
 the reference guide did not sufficiently answer your question.
* Next to the mailing list we also monitor Stack Overflow for any questions which are tagged with `axon`.

## Feature requests and issue reporting

We use GitHub's [issue tracking system]((https://github.com/AxonFramework/AxonFramework/issues)) for new feature request,
 framework enhancements and bugs. 
Prior to filing an issue, please verify that it's not already reported by someone else.

When filing bugs:
* A description of your setup and what's happening helps us to figure out what the issue might be
* Do not forget to provide the framework version you're using
* If possible, share a stack trace, using the Markdown semantic ```

When filing features:
* A description of the envisioned addition or enhancement should be provided
* (Pseudo-)Code snippets showing what it might look like help us understand your suggestion better 
* If you have any thoughts on where to plug this into the framework, that would be very helpful too
* Lastly, we value contributions to the framework highly. So please provide a Pull Request as well!
 