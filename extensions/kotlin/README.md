# Axon Framework - Kotlin Extension

## Kotlin conventions

* No need for intermediate packages, root class marks the beginning of the hierarchy
* We do not produce separate artifacts for each axonframework module (messagning, eventsourcing, ...), however we reflect the hierarchy in the package names (extensions for `axon-messaging` are in `org.axonframework.extension.kotlin.messaging`, ...)


## Receiving help

Are you having trouble using the extension?
We'd like to help you out the best we can!
There are a couple of things to consider when you're traversing anything Axon:

* Checking the [documentation](https://docs.axoniq.io/home/) should be your first stop,
  as the majority of possible scenarios you might encounter when using Axon should be covered there.
* If the Reference Guide does not cover a specific topic you would've expected,
  we'd appreciate if you could post a [new thread/topic on our library fourms describing the problem](https://discuss.axoniq.io/c/26).
* There is a [forum](https://discuss.axoniq.io/) to support you in the case the reference guide did not sufficiently answer your question.
Axon Framework and Server developers will help out on a best effort basis.
Know that any support from contributors on posted question is very much appreciated on the forum.
* Next to the forum we also monitor Stack Overflow for any questions which are tagged with `axon`.

## Feature requests and issue reporting

We use GitHub's [issue tracking system](https://github.com/AxonFramework/AxonFramework/issues) for new feature
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

### Producing JavaDocs and Sources archive

Please execute the following command line if you are interested in producing KDoc and Source archives:

    ./mvnw clean install -Pdocs-and-sources

