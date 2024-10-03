# Documentation For Axon Framework - Kotlin Extension.

This folder contains the docs related to the Kotlin Extension for Axon Framework. The docs in this folder are written as part of the [AxonIQ Library](https://library.axoniq.io), and are [written in Ascii and built with Antora.](https://library.axoniq.io/contribution_guide/overview/platform.html)

The following are the current documentation sources (folders):

- `extension-guide` : [The Kotlin Extension Guide](https://library.axoniq.io/kotlin-extension-reference/index.html)

## Contributing to the docs.

You are welcome to contribute to these docs. Whether you want to fix a typo, or you find something missing, something that is not clear or can be improved, or even if you want to write an entire piece of docs to illustrate something that could help others to understand the use of the Bike Rental App, you are more than welcome to send a Pull Request to this GitHub repository. Just make sure you follow the guidelines explained in [AxonIQ Library Contribution Guide](https://library.axoniq.io/contribution_guide/index.html)

## Building and testing these docs locally.

If you want to build and explore the docs locally (because you have made changes or before contributing), you can use the Antora's build file in `docs/_playbook` folder.

You can check the [detailed information on how the process to build the docs works](https://library.axoniq.io/contribution_guide/overview/build.html), but in short, all you have to do is:

1. Make sure you have Node (a LTS version is preferred), Antora and Vale installed in your system.
2. CD to the `docs/_playbook` folder.
3. Run `npx antora playbook.yaml`. Antora will generate the set of static html files under `docs/_playbook/build/site`
4. Move to `docs/_playbook/build/site` and execute some local http server to serve files in that directory. For example by executing `python3 -m http.server 8070`
5. Open your browser and go to `http://localhost:8070`. You should be able to navigate the local version of the docs.
