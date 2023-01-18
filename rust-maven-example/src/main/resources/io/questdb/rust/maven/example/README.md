# Embedding binaries in the JAR

# Copied Rust Binaries

Any files in the `${project.baseDir}/src/main/resources/` directory will automatically be copied to the root of
the packaged `.jar` file.

The `rust-maven-plugin` can be configured to copy the generated binaries to another directory.

We use this feature in this example to store the compiled Rust binaries in the `libs` directory.
They can then be extracted to a temporary directory and used by the application at runtime.

```java
SomeClassFromTheSameJar.class.getResourceAsStream("/io/questdb/rust/maven/example/libs/linux_amd64/libstr_reverse.so");
```

The contents can then be placed in a temporary directory and loaded via `System.load()`.

To simplify this step, and to allow bundling multiple binaries in the jar for various platforms, we suggest using the
`io.questdb.jar.jni.JarJniLoader` from the `jar-jni` library.

```java
JarJniLoader.loadLibrary("str_reverse");
```

# Cleaning binaries 

This example's `pom.xml` file is also configured to clean the contents of this directory when running `mvn clean`.
