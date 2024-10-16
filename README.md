<p align="center">
  <a href="https://axoniq.io/">
    <img src="https://www.axoniq.io/hubfs/axoniq-light.svg" alt="Axon Framework logo" width="600" height="200">
  </a>
</p>

<h1 align="center"></h1>

<p align="center">
  Build modern event-driven systems with AxonIQ technology
  <br>
  <a href="https://www.axoniq.io/products/axon-framework"><strong>Learn more at our website »</strong></a>
  <br>
  <br>
  <a href="https://www.axoniq.io/products/axon-server">Axon Framework</a>
  ·
  <a href="https://www.axoniq.io/products/axon-server">Axon Server</a>
  ·
  <a href="https://www.axoniq.io/products/axoniq-console">AxonIQ Console</a>

</p>

#

<p><br/></p>
<img src="https://www.axoniq.io/hubfs/axon-framework-line-light.png" alt="Axon Framework logo" width="500">

The Axon Framework is an open source framework that's 100% Java and enables developers to build scalable and maintainable applications using a message-driven approach. It simplifies the complexities of developing distributed systems by providing a structured way to handle commands, events, and queries within your application. [If you're not familiar with the Event Sourcing pattern](https://www.axoniq.io/concepts/cqrs-and-event-sourcing), you can learn more about it on our website.

At its core, the Axon Framework encourages an architecture where components communicate through messages, which promotes loose coupling and increases flexibility. This messaging system allows different parts of your application to interact without needing to know the internal workings of each other, making your system more modular and easier to manage. If you already use tools and technologies that enable your applications to utilize a pub/sub pattern, then you already understand the basics of message driven and event driven architectures.


<p align="center">
<img src="https://www.axoniq.io/hubfs/framework-medium-image.webp" alt="Axon Framework diagram" align="center" width="500">
</p>


The Axon Framework further enhances your applications and services by enabling you to support for Event Sourcing, a powerful architectural pattern where state changes are recorded as a sequence of events. Why is this important? Well, instead of just storing the current state in a traditional database, every change is logged, providing a complete history of how the data arrived at its current form. This can be incredibly useful for auditing, debugging, and even recreating past states of your application when necessary.

For Java developers working with microservices, the Axon Framework offers tools to manage the complexities inherent in distributed systems. By leveraging its messaging and event-handling capabilities, you can build applications that are not only scalable and robust but also easier to extend and maintain over time.

## How Can I Get Started with the Axon Framework?

We highly recommend all developers to get started by going to the [AxonIQ Documentation Portal](https://docs.axoniq.io/home/), which provides links to our tutorials, guides, and reference documentation.

Furthermore, below are several other helpful resources:
* [Go to the Axon Framework Github repo](https://github.com/AxonFramework/AxonFramework) to view the source code and follow the project 
* [Read the tutorial](https://docs.axoniq.io/bikerental-demo/main/) on building a Bike Rental application from scratch
* [Watch our in-depth video training courses](https://academy.axoniq.io/) in the AxonIQ Academy
* [Ask questions](https://discuss.axoniq.io/) in our help forum, Discuss.
* [See additional code samples](https://github.com/AxonIQ/code-samples) in our code samples repository 



<p><br/></p>
<img src="https://www.axoniq.io/hubfs/axon-server-line-light.png" alt="Axon Server logo" width="500">

For developers who are serious about Event Sourcing, we offer the Axon Server. The Axon Server is designed to simplify the development event-driven applications by acting as both an Event Store and a message router. Therefore, it serves as a central hub for managing and distributing events, commands, and queries within your application ecosystem. By handling these critical aspects, Axon Server allows developers to focus more on business logic rather than the complexities of communication and data storage in distributed systems.

<p align="center">
<img src="https://www.axoniq.io/hubfs/image%2017.jpg" alt="Axon Server diagram" align="center" width="500">
</p>

When developing microservices, coordinating interactions between multiple services can become quite challenging. This is especially the case as the system scales to handle more users or additional features requested by customers and stakeholders. Axon Server addresses this by providing seamless and scalable message routing between services. It ensures that messages reach their intended targets and that events are efficiently broadcasted to all interested parties. 

In addition to functioning as a message router, Axon Server enables apps and microservices to be Event Sourced. This means that all changes in the application state are stored as a sequence of events. This allows the application to reconstruct its state at any point in time by replaying these events. For developers unfamiliar with event sourcing, this means you have a complete history of what happened in your system, which is invaluable for debugging, auditing, and understanding complex behaviors. This is a revolutionary approach to traditional application development, however the architectural pattern is proven to create better and more resilient software systems.

By incorporating Axon Server into your applications, especially those built with microservices, you gain a robust platform for managing the flow of data and commands across your system. It abstracts the complexities of message handling and event storage, enabling you to build scalable, maintainable, and high-performing applications. This allows you to deliver features faster and adapt more readily to changing business requirements.

## How Can I Get Started with Axon Server?

If you're getting started with the Axon Server, then we recommend that you go to the [AxonIQ Documentation Portal](https://docs.axoniq.io/home/), which provides links to our tutorials, guides, and reference documentation.

Furthermore, below are several other helpful resources:
* [Download the latest release](https://www.axoniq.io/download) of Axon Server. It's free and easy to install and customize
* [Read the tutorial](https://docs.axoniq.io/bikerental-demo/main/) on building a Bike Rental application from scratch
* [Watch our in-depth video training courses](https://academy.axoniq.io/)s in the AxonIQ Academy
* [Ask questions](https://discuss.axoniq.io/) in our help forum, Discuss.




# AxonIQ Console

The Axon Framework is an open source framework that's 100% Java and enables developers to build scalable and maintainable applications using a messaging-driven approach. It simplifies the complexities of developing distributed systems by providing a structured way to handle commands, events, and queries within your application.

At its core, the Axon Framework encourages an architecture where components communicate through messages, which promotes loose coupling and increases flexibility. This messaging system allows different parts of your application to interact without needing to know the internal workings of each other, making your system more modular and easier to manage.

One of the key features of the framework is its support for event sourcing, a pattern where state changes are recorded as a sequence of events. Instead of just storing the current state, every change is logged, providing a complete history of how the data arrived at its current form. This can be incredibly useful for auditing, debugging, and even recreating past states of your application when necessary.

Additionally, the Axon Framework facilitates the implementation of event-driven architectures. In this paradigm, components react to events as they occur, which can lead to more responsive and resilient systems. By processing events asynchronously, your application can handle high loads more effectively and remain responsive under stress.

For Java developers working with microservices, the Axon Framework offers tools to manage the complexities inherent in distributed systems. By leveraging its messaging and event-handling capabilities, you can build applications that are not only scalable and robust but also easier to extend and maintain over time.

## How to Get Started with Axon Framework

Numerous resources can help you on your journey in using Axon Framework.
A good starting point is [AxonIQ Documentation Portal](https://docs.axoniq.io/home/), which provides links to our tutorials, guides, and reference documentation.

Furthermore, below are several other helpful resources:
* [Read the tutorial](https://docs.axoniq.io/bikerental-demo/main/) on building a Bike Rental application from scratch.
* [Watch our in-depth video training courses](https://academy.axoniq.io/)s in the AxonIQ Academy
* [Ask questions](https://discuss.axoniq.io/) in our help forum, Discuss.
* [See additional code samples](https://github.com/AxonIQ/code-samples) in our code samples repository 


