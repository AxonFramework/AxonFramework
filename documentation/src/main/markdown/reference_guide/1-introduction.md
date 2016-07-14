Introduction
============

Axon is a lightweight framework that helps developers build scalable and extensible applications by addressing these concerns directly in the architecture. This reference guide explains what Axon is, how it can help you and how you can use it.

If you want to know more about Axon and its background, continue reading in [Axon Framework Background](#axon-framework-background). If you're eager to get started building your own application using Axon, go quickly to [Getting Started](#getting-started). If you're interested in helping out building the Axon Framework, [Contributing](#contributing) will contain the information you require. All help is welcome. Finally, this chapter covers some legal concerns in [License](#license).

Axon Framework Background
=========================

A brief history
---------------

The demands on software projects increase rapidly as time progresses. Companies no longer accept a brochure-like homepage to promote their business; they want their (web)applications to evolve together with their business. That means that not only projects and code bases become more complex, it also means that functionality is constantly added, changed and (unfortunately not enough) removed. It can be frustrating to find out that a seemingly easy-to-implement feature can require development teams to take apart an entire application. Furthermore, today's web applications target the audience of potentially billions of people, making scalability an indisputable requirement.

Although there are many applications and frameworks around that deal with scalability issues, such as GigaSpaces and Terracotta, they share one fundamental flaw. These stacks try to solve the scalability issues while letting developers develop applications using the layered architecture they are used to. In some cases, they even prevent or severely limit the use of a real domain model, forcing all domain logic into services. Although that is faster to start building an application, eventually this approach will cause complexity to increase and development to slow down.

The Command Query Responsibility Segregation (CQRS) pattern addresses these issues by drastically changing the way applications are architected. Instead of separating logic into separate layers, logic is separated based on whether it is changing an application's state or querying it. That means that executing commands (actions that potentially change an application's state) are executed by different components than those that query for the application's state. The most important reason for this separation is the fact that there are different technical and non-technical requirements for each of them. When commands are executed, the query components are (a)synchronously updated using events. This mechanism of updates through events, is what makes this architecture is extensible, scalable and ultimately more maintainable.

> **Note**
>
> A full explanation of CQRS is not within the scope of this document. If you would like to have more background information about CQRS, visit the Axon Framework website:[www.axonframework.org](http://www.axonframework.org/). It contains links to background information.

Since CQRS is so fundamentally different than the layered-architecture which dominates the software landscape nowadays, it is quite hard to grasp. It is not uncommon for developers to walk into a few traps while trying to find their way around this architecture. That's why Axon Framework was conceived: to help developers implement CQRS applications while focusing on the business logic.

What is Axon?
-------------

Axon Framework helps build scalable, extensible and maintainable applications by supporting developers apply the Command Query Responsibility Segregation (CQRS) architectural pattern. It does so by providing implementations of the most important building blocks, such as aggregates, repositories and event buses (the dispatching mechanism for events). Furthermore, Axon provides annotation support, which allows you to build aggregates and event listeners without tying your code to Axon specific logic. This allows you to focus on your business logic, instead of the plumbing, and helps you to make your code easier to test in isolation.

Axon does not, in any way, try to hide the CQRS architecture or any of its components from developers. Therefore, depending on team size, it is still advisable to have one or more developers with a thorough understanding of CQRS on each team. However, Axon does help when it comes to guaranteeing delivering events to the right event listeners and processing them concurrently and in the correct order. These multi-threading concerns are typically hard to deal with, leading to hard-to-trace bugs and sometimes complete application failure. When you have a tight deadline, you probably don't even want to care about these concerns. Axon's code is thoroughly tested to prevent these types of bugs.

The Axon Framework consists of a number of modules (jars) that provide the tools and components to build a scalable infrastructure. The Axon Core module provides the basic APIs for the different components, and simple implementations that provide solutions for single-JVM applications. The other modules address scalability or high-performance issues, by providing specialized building blocks.

When to use Axon?
-----------------

Will each application benefit from Axon? Unfortunately not. Simple CRUD (Create, Read, Update, Delete) applications which are not expected to scale will probably not benefit from CQRS or Axon. Fortunately, there is a wide variety of applications that does benefit from Axon.

Applications that will most likely benefit from CQRS and Axon are those that show one or more of the following characteristics:

-   The application is likely to be extended with new functionality during a long period of time. For example, an online store might start off with a system that tracks progress of Orders. At a later stage, this could be extended with Inventory information, to make sure stocks are updated when items are sold. Even later, accounting can require financial statistics of sales to be recorded, etc. Although it is hard to predict how software projects will evolve in the future, the majority of this type of application is clearly presented as such.

-   The application has a high read-to-write ratio. That means data is only written a few times, and read many times more. Since data sources for queries are different to those that are used for command validation, it is possible to optimize these data sources for fast querying. Duplicate data is no longer an issue, since events are published when data changes.

-   The application presents data in many different formats. Many applications nowadays don't stop when showing information on a web page. Some applications, for example, send monthly emails to notify users of changes that occurred that might be relevant to them. Search engines are another example. They use the same data your application does, but in a way that is optimized for quick searching. Reporting tools aggregate information into reports that show data evolution over time. This, again, is a different format of the same data. Using Axon, each data source can be updated independently of each other on a real-time or scheduled basis.

-   When an application has clearly separated components with different audiences, it can benefit from Axon, too. An example of such application is the online store. Employees will update product information and availability on the website, while customers place orders and query for their order status. With Axon, these components can be deployed on separate machines and scaled using different policies. They are kept up-to-date using the events, which Axon will dispatch to all subscribed components, regardless of the machine they are deployed on.

-   Integration with other applications can be cumbersome work. The strict definition of an application's API using commands and events makes it easier to integrate with external applications. Any application can send commands or listen to events generated by the application.

Getting started
===============

This section will explain how you can obtain the binaries for Axon to get started. There are currently two ways: either download the binaries from our website or configure a repository for your build system (Maven, Gradle, etc).

Download Axon
-------------

You can download the Axon Framework from our downloads page: [axonframework.org/download](http://www.axonframework.org/download).

This page offers a number of downloads. Typically, you would want to use the latest stable release. However, if you're eager to get started using the latest and greatest features, you could consider using the snapshot releases instead. The downloads page contains a number of assemblies for you to download. Some of them only provide the Axon library itself, while others also provide the libraries that Axon depends on. There is also a "full" zip file, which contains Axon, its dependencies, the sources and the documentation, all in a single download.

If you really want to stay on the bleeding edge of development, you can clone the Git repository: <git://github.com/AxonFramework/AxonFramework.git>, or visit <https://github.com/AxonFramework/AxonFramework> to browse the sources online.

Configure Maven
---------------

If you use maven as your build tool, you need to configure the correct dependencies for your project. Add the following code in your dependencies section:

``` xml
<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-core</artifactId>
    <version></version>
</dependency>
```

Most of the features provided by the Axon Framework are optional and require additional dependencies. We have chosen not to add these dependencies by default, as they would potentially clutter your project with artifacts you don't need.

Infrastructure requirements
---------------------------

Axon Framework doesn't impose many requirements on the infrastructure. It has been built and tested against Java 6, making that more or less the only requirement.

Since Axon doesn't create any connections or threads by itself, it is safe to run on an Application Server. Axon abstracts all asynchronous behavior by using `Executor`s, meaning that you can easily pass a container managed Thread Pool, for example. If you don't use an Application Server (e.g. Tomcat, Jetty or a stand-alone app), you can use the `Executors` class or the Spring Framework to create and configure Thread Pools.

When you're stuck
-----------------

While implementing your application, you might run into problems, wonder about why certain things are the way they are, or have some questions that need an answer. The Axon Users mailing list is there to help. Just send an email to <axonframework@googlegroups.com>. Other users as well as contributors to the Axon Framework are there to help with your issues.

If you find a bug, you can report them at [axonframework.org/issues](http://www.axonframework.org/issues). When reporting an issue, please make sure you clearly describe the problem. Explain what you did, what the result was and what you expected to happen instead. If possible, please provide a very simple Unit Test (JUnit) that shows the problem. That makes fixing it a lot simpler.

Contributing to Axon Framework
==============================

Development on the Axon Framework is never finished. There will always be more features that we like to include in our framework to continue making development of scalable and extensible application easier. This means we are constantly looking for help in developing our framework.

There are a number of ways in which you can contribute to the Axon Framework:

-   You can report any bugs, feature requests or ideas for improvements on our issue page: [axonframework.org/issues](http://www.axonframework.org/issues). All ideas are welcome. Please be as exact as possible when reporting bugs. This will help us reproduce and thus solve the problem faster.

-   If you have created a component for your own application that you think might be useful to include in the framework, send us a patch or a zip containing the source code. We will evaluate it and try to fit it in the framework. Please make sure code is properly documented using javadoc. This helps us to understand what is going on.

-   If you know of any other way you think you can help us, do not hesitate to send a message to the [Axon Framework mailing list](mailto:axonframework@googlegroups.com).

Commercial Support
==================

Axon Framework is open source and freely available for anyone to use. However, if you have very specific requirements, or just want to be assured of someone to be standby to help you in case of trouble, Trifork provides several commercial support services for Axon Framework. These services include Training, Consultancy and Operational Support and are provided by the people that know Axon more than anyone else.

For more information about Trifork and its services, visit [www.trifork.nl](http://www.trifork.nl).

License information
===================

The Axon Framework and its documentation are licensed under the Apache License, Version 2.0. You may obtain a copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the [License](http://www.apache.org/licenses/LICENSE-2.0) for the specific language governing permissions and limitations under the License.
