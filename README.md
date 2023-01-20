# rust-maven-plugin

<img src="./artwork/logo_outline_text.svg" alt="rust-maven-plugin">

Build Rust Cargo crates within a Java Maven Project with this plugin.

The use case is to simplify the build process of
[Rust JNI libs](https://crates.io/crates/jni) inside a Java
[Maven](https://maven.apache.org/) project.

# Features

* Calls `cargo build` as part of a `mvn compile` step.

* Helpfully informs how to install Rust if Rust is not found.

* Uses Maven's target directory (i.e. `${project.build.directory}`) as the
  `cargo build --target-dir` so `mvn clean` also cleans the Rust crate without
  additional setup in your `pom.xml`.

* Optionally:
  * Can be configured to copy the compiled cdylib(s) to the
    `${project.build.directory}/classes/` directory so the binaries ends up
    bundled in the JAR.

  * As a separate runtime dependency we also provide [`jar-jni`](jar-jni/),
    a helper java library to load the libs from the JAR using a sub-directory
    per `os.name`/`os.arch` so the JAR can contain binaries for multiple
    platforms.

# Status
* Pre-production:
  * Building, cleaning, and bundling into a `.jar` now works.
  * Running Rust tests (calling `cargo test`) isn't implemented yet.
  * Not yet available on maven central:
    In the meantime `cd rust-maven-plugin && mvn install`.

If you want to help out, check out our
[open issues](https://github.com/questdb/rust-maven-plugin/issues).

# Dev Commands

To build the project and example:

```shell
git clone https://github.com/questdb/rust-maven-plugin.git
cd rust-maven-plugin
mvn clean package
java -cp "./rust-maven-example/target/rust-maven-example-1.0.0-SNAPSHOT.jar:./jar-jni/target/jar-jni-1.0.0-SNAPSHOT.jar" io.questdb.rust.maven.example.Main
```

To run Maven goals directly from the command line.

```shell
cd rust-maven-plugin
mvn install
mvn io.questdb:rust-maven-plugin:build -Drelease=true
```

# Example
See `rust-maven-example` directory for a working example.

# Special thanks

* OktaDev for covering custom Maven plugins on YouTube: https://www.youtube.com/watch?v=wHX4j0z-sUU
* The CMake maven plugin project: https://github.com/cmake-maven-project/cmake-maven-project
