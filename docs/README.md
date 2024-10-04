# Documentation For Axon Framework.

This folder contains the docs related to the Axon Framework project. The docs in this folder are written as part of the [AxonIQ Documentation](https://docs.axoniq.io), and are [written in Ascii and built with Antora.](https://docs.axoniq.io/contribution_guide/overview/platform.html)

The following are the current documentation sources (folders):

- `af-fundamentals-tutorial`: [A tutorial covering Axon Framework's fundamental components and features.](https://docs.axoniq.io/axon_framework_fundamentals/index.html)
- `identifier-generation-guide` : [Guide that covers several considerations in regards to identifier generation in Axon Framework-based applications.](https://docs.axoniq.io/identifier-generation-guide/index.html)
- `message-handler-tunning-guide` : [Guide that covers the message handler tuning in your Axon Framework applications.](https://docs.axoniq.io/message-handler-tuning-guide/index.html)
- `meta-annotations-guide` : [Guide that covers several considerations in regards to creating Meta Annotations for Axon Framework-based applications.](https://docs.axoniq.io/meta-annotations-guide/index.html)
- `old-reference-guide` : [The Axon Framework former reference guide migrated from former docs.axoniq.io](https://docs.axoniq.io/axon-framework-reference/introduction.html)
- `rdbms-tunning-guide` : [Guide that covers several considerations in regards to tuning the database for events.](https://docs.axoniq.io/rdbms-tuning-guide/index.html)


## Contributing to the docs.

You are welcome to contribute to these docs. Whether you want to fix a typo, or you find something missing, something that it's not clear or can be improved, or even if you want to write an entire piece of docs to illustrate something that could help others to understand the use of the Bike Rental App, you are more than welcome to send a Pull Request to this github repository. Just make sure you follow the guidelines explained in [AxonIQ Library Contribution Guide](https://docs.axoniq.io/contribution_guide/index.html)

## Building and testing this docs locally.

If you want to build and explore the docs locally (because you have made changes or before contributing), you can use the Antora's build file in `docs/_playbook` folder.

You can check the [detailed information on how the process to build the docs works](https://docs.axoniq.io/contribution_guide/overview/build.html), but in short, all you have to do is: 

1. Make sure you have Node (a LTS version is preferred), Antora and Vale installed in your system.
2. CD to the `docs/_playbook` folder.
3. Run `npx antora playbook.yaml`. Antora will generate the set of static html files under `docs/_playbook/build/site`
4. Move to `docs/_playbook/build/site` and execute some local http server to serve files in that directory. For example by executing `python3 -m http.server 8070`
5. Open your browser and go to `http://localhost:8070`. You should be able to navigate the local version of the docs.
