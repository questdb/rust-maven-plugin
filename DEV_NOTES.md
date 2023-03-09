# Developer's Notes

## Building

To build the project and run the example:

```shell
git clone https://github.com/questdb/rust-maven-plugin.git
cd rust-maven-plugin
mvn clean package
java -cp "./rust-maven-example/target/*:./jar-jni/target/*" io.questdb.example.rust.Main
```

_(Substituting `:` for `;` if developing on Windows)_

## Testing Against Another Project

To test your changes against another project you need to install
the `jar-jni` and `rust-maven-plugin` artifacts locally in your Maven cache:

```shell
cd jar-jni
mnv clean install
cd ../rust-maven-plugin
mvn clean install
```

## Cutting a new release

### Maven Upload

To cut a new release and upload to Maven Central, run:

```
mvn -B release:prepare release:perform
```

Here the `-B` flag will bump up the release version automatically.
The bump is a patch release, e.g. `1.0.1` to `1.0.2`.

For full docs, see https://maven.apache.org/maven-release/maven-release-plugin/examples/prepare-release.html.

### Updating `README.md`.

The documentation in `README.md` mentions the version explicitly.

To update it and discourage the use of old versions, run the
`update_doc_version.py` script.

```
python3 update_doc_version.py
```

This will make changes without checking them in.
