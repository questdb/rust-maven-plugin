# rust-maven-plugin
Build Rust Cargo crates within a Java Maven Project

# Status
Most things don't work yet.

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
