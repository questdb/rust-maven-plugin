# rust-maven-plugin

<img src="./artwork/logo_outline_text.svg" alt="rust-maven-plugin">

Build Rust Cargo crates within a Java Maven Project.

```shell
$ mvn clean package
...
[INFO] --- rust-maven-plugin:1.0.0:build (str-reverse) @ rust-maven-example ---
[INFO] Working directory: /home/adam/questdb/repos/rust-maven-plugin/rust-maven-example/src/main/rust/str-reverse
[INFO] Environment variables:
[INFO]   REVERSED_STR_PREFIX='Great Scott, A reversed string!'
[INFO] Running: cargo build --target-dir /home/adam/questdb/repos/rust-maven-plugin/rust-maven-example/target/rust-maven-plugin/str-reverse --release
[INFO]    Compiling proc-macro2 v1.0.49
[INFO]    Compiling quote v1.0.23
[INFO]    Compiling unicode-ident v1.0.6
[INFO]    Compiling syn v1.0.107
...
```

# Plugin Features

* The plugin delegates the build to `cargo` and supports most of `cargo build`'s features.
* The primary use case is to simplify the build process of
  [Rust JNI libs](https://crates.io/crates/jni) inside a Java
  [Maven](https://maven.apache.org/) project.
* Additionally, the plugin can also compile binaries.
* The plugin can copy complied binaries to a custom location and so they can be bundled inside of `.jar` files.
* Support for invoking `cargo test` during `mvn test`.
* Points to https://www.rust-lang.org/tools/install if `cargo` isn't found.

## Optional supporting loader library

For your convenience, we've also made `jar-jni` available:
An optional Java library to load JNI dynamic libraries from JARs.

Both the plugin and the library support a common directory naming convention
to organize and find compiled artifacts for a multitude of platforms.

# Complete Example
See the [`rust-maven-example`](rust-maven-example/) directory for a working
example.

It also uses the `jar-jni` library to load the Rust binaries from the compiled
JAR file.

# Basic Configuration

Edit your `pom.xml` to add the plugin:

```xml
<project ...>
    ...

    <!-- Note: Don't add rust-maven-plugin to <dependencies>. -->

    <build>
        <plugins>
            <plugin>
                <groupId>org.questdb</groupId>
                <artifactId>rust-maven-plugin</artifactId>
                <version>1.0.0</version>
                <executions>
                    <execution>
                        <id>rust-build-id</id>
                        <goals>
                            <goal>build</goal>
                        </goals>
                        <configuration>
                            <path>src/main/rust/your-rust-crate</path>
                            <copyTo>${project.build.directory}/classes/io/questdb/example/rust/libs</copyTo>
                            <copyWithPlatformDir>true</copyWithPlatformDir>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            ...
        </plugins>
    </build>

</project>
```

Here, `<path>..</path>` is the path to the Rust crate to build, and it is relative to the `pom.xml` file itself.
The plugin will invoke `cargo build` on the crate.

The `<id>` is an arbitrary string that you can use to identify the execution.
It does not need to match the crate name.

If you need to build multiple crates, you can add multiple executions.

# Testing

The plugin can also invoke `cargo test` during `mvn test`.

To enable running tests:

* Duplicate the `<execution>` block above.
* Change it's `<id>` to a new name.
* Change the `<goal>` to `test`.

```xml
<execution>
    <id>str-reverse-test</id>
    <goals>
        <goal>test</goal>
    </goals>
    <configuration>
        <path>src/main/rust/str-reverse</path>
    </configuration>
</execution>
```

# Customizing the build and tests steps

The settings below go in the `<configuration>` section of the `<execution>` block.

## Custom path to the `cargo` command

If `cargo` isn't in your `PATH`, you can specify the path to the `cargo` command with the `<cargoPath>`
configuration option.

You can also specify this on the command line via `mvn ... -DcargoPath=...`.

## Verbosity

The plugin can be configured to forward various verbosity flags to `cargo` by setting
`<verbosity>-v</verbosity>` (or other value) in the `<configuration>` block.

Accepted values are:

| Value                                 | Description                                     |
|---------------------------------------|-------------------------------------------------|
| `<verbosity></verbosity>` (or no tag) | Default - no additional flags passed to `cargo` |
| `<verbosity>-q</verbosity>`           | Quiet                                           |
| `<verbosity>-v</verbosity>`           | Verbose                                         |
| `<verbosity>-vv</verbosity>`          | Very verbose                                    |

## Release builds

The plugin can be configured to build in release mode by setting
`<release>true</release>` in the `<configuration>` block.

Building `--release` will cut down binary size considerably and should be taken
into consideration when shipping binaries in `.jar` files.

## Specifying Crate Features

The equivalent of `cargo build --features feat1,feat2,feat3` is

```xml
<features>
    <feature>feat1</feature>
    <feature>feat2</feature>
    <feature>feat3</feature>
</features>
```

You can also specify `--all-features` via `<all-features>true</all-features>` and `--no-default-features`
via `<no-default-features>true</no-default-features>`.

Note that you can drive features with maven profiles by introducing variables.

```xml
<!-- in the plugin configuration -->
<features>
    <feature>$rustFeature1</feature>
    <feature>$rustFeature2</feature>
</features>
```

```xml
<!-- in the profiles section -->
<profile>
    <id>feat-ssl</id>
    <properties>
        <rustFeature1>ssl</rustFeature1>
        <rustFeature1>use-rustls</rustFeature1>
    </properties>
</profile>
```

Then building with `mvn package -P feat-ssl` will call
`cargo build --features ssl,use-rustls`.

## Additional cargo arguments

Additional arguments to can go in the `<extra-args>` configuration section.

```xml
<extra-args>
    <extra-arg>--verbose</extra-arg>
    <extra-arg>--color=always</extra-arg>
</extra-args>
```

## Overriding Environment Variables

The plugin can be configured to override environment variables during the build.
This might be useful for setting `RUSTFLAGS`.

In the `<configuration>` section, add:

```xml
<environmentVariables>
    <RUSTFLAGS>-C target-cpu=native</RUSTFLAGS>
</environmentVariables>
```

# Cleaning the Rust build

Regular `mvn clean` will also clean the Rust build without additional config.
This is because the plugin builds crates inside Maven's `target` build
directory, via `cargo build --target-dir ...`.

# De-duplicating build directories when invoking `cargo build` without Maven

If you (or your IDE) end up invoking `cargo build` on your Rust crate without
the plugin, you'll notice this creates a duplicate `target` dir, inside the
crate's directory, that will not be cleaned at the next `mvn clean`.

To avoid this duplicate `target` directory problem, consider adding
`.cargo/config.toml` files configured to match the `--target-dir` argument
passed by this plugin.

See [.cargo/config.toml](rust-maven-example/src/main/rust/str-reverse/.cargo/config.toml)
from the `str-reverse` crate in the example.

# Bundling binaries in the `.jar` file

The `<copyTo>` configuration (as shown in the example) allows copying the
binaries any path. The example however choses to copy them to
`${project.build.directory}/classes/...`. Anything placed there gets bundled
into the JAR file.
The `classes` directory sits within the `target` directory and outside of the
source tree.

## Binaries in source tree

Placing binaries in the source tree may be the "pragmatic" approach if you need
to support IntelliJ which, by default, will not actually invoke `maven compile`
during its usual operation.

If you know a better way around this in IntelliJ do contact us!

If you prefer to keep your binaries in the source tree, then you instead
configure to copy binaries to the [`resources`](https://stackoverflow.com/questions/25786185/what-is-the-purpose-for-the-resource-folder-in-maven) directory
instead:

```xml
<copyTo>src/main/resources/io/questdb/example/rust/libs</copyTo>
<copyWithPlatformDir>true</copyWithPlatformDir>
```

In such case, you may opt to move the `rust-maven-plugin` inside a
[Maven Profile](https://maven.apache.org/guides/introduction/introduction-to-profiles.html) and only build the Rust
code when you need to.

```xml
<project ...>
    ...
    <profiles>
        <profile>
            <id>rust</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.questdb</groupId>
                        <artifactId>rust-maven-plugin</artifactId>
                        <version>1.0.0</version>
...
```

You can then enable the profile in Maven via `mvn clean package -P rust ...`.

## Supporting Multiple Platforms

During the binary copy step, the `<copyWithPlatformDir>true</copyWithPlatformDir>` config setting (used in the examples
above) will further nest the binaries in a directory named after the platform.

```
target
    classes
        io/questdb/example/rust/libs/
            linux-amd64/libstr_reverse.so
            mac_os_x-aarch64/libstr_reverse.dylib
            windows-amd64/str_reverse.dll
```

If you only intend to target one single platform (e.g. linux-amd64), then you
don't need `<copyWithPlatformDir>true</copyWithPlatformDir>` and the plugin will not create a nested directory.

# Loading binaries from the `.jar` with `jar-jni`

The `jar-jni` library is configured as so:

```xml
<!-- pom.xml -->
<project ...>
    ...
    <dependencies>
      <dependency>
          <groupId>org.questdb</groupId>
          <artifactId>jar-jni</artifactId>
          <version>1.0.0</version>
      </dependency>
    </dependencies>
    ...
</project>
```

It helps with bundling JNI native code in `.jar` files by establishing a directory
naming convention for organising binaries for different operating systems and
architectures.

Assuming you've compiled with `<copyWithPlatformDir>true</copyWithPlatformDir>`, load
the binary from the `.jar` file with:

```java
JarJniLoader.loadLib(
    Main.class,

    // A platform-specific path is automatically suffixed to path below.
    "/io/questdb/example/rust/libs",

    // The "lib" prefix and ".so|.dynlib|.dll" suffix are added automatically as needed.
    "str_reverse");
```

If instead you compiled with `<copyWithPlatformDir>false</copyWithPlatformDir>`, then:

```java
JarJniLoader.loadLib(
    Main.class,
    "/io/questdb/example/rust/libs",
    "str_reverse",
    null);
```

# Contributing & Support

* Test cases, features, docs, tutorials, etc are always welcome.
* [Raise an issue](https://github.com/questdb/rust-maven-plugin/issues/new/choose) if you find bugs.
* We've got a list of open [issues](https://github.com/questdb/rust-maven-plugin/issues).
* Raise a pull request if you need a new feature.

If you want to talk to us, we're on [Slack](https://slack.questdb.io/).

Also read the [Developer's Notes](DEV_NOTES.md) with instructions on building
and running from source.
