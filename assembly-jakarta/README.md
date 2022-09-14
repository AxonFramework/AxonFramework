# What is this for?

Axon Framework is built based on JDK 8.
As Oracle [decided](https://blogs.oracle.com/javamagazine/post/transition-from-java-ee-to-jakarta-ee) to block any usage of the `javax` namespace in more recent releases, in favor of `jakarta`, this imposes a strain on the framework.
To solve this, the modules containing `javax` dependencies have a `jakarta` equivalent.
In these, the sources are adjusted during build time, to replace the dependencies correctly.

We perform this process for the following modules:

- axon-configuration
- axon-eventsourcing
- axon-integrationtests
- axon-messaging
- axon-modelling

Each of these modules thus has a `{module-name}-jakarta` equivalent.
Furthermore, each of these `jakarta` copy modules _only_ contains a `pom.xml` to define this process.

Since the only file present is a `pom.xml`, these modules do not construct any `*-sources.jar` or `*-javadoc.jar`.
This breaks a requirement from Sonatype, which expect the main, sources, and JavaDoc `jar` to be present *at all times*.
To solve this predicament, we've followed this suggestion from [Sonatype's documentation](https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources):

> If, for some reason (for example, license issue or it's a Scala project), you can not provide -sources.jar or -javadoc.jar, please make fake -sources.jar or -javadoc.jar with simple README inside to pass the checking. 
> We do not want to disable the rules because some people tend to skip it if they have an option and we want to keep the quality of the user experience as high as possible.

This brings us to the reason why these jars do not contain actual sources or JavaDoc.
More concretely, we use the [Maven Assembly Plugin](https://maven.apache.org/plugins/maven-assembly-plugin/index.html) to construct these jar-files, containing the `README.md` you're reading right now.

## But, where are the sources or documentation?

If you are looking for either, we thus recommend to check out the original module's source and/or documentation.