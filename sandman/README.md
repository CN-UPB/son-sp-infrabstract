# PG-SanDMAN Translator

The Translator constitutes the link between the SONATA descriptor editor and the son-emu datacenter emulator.

## Build

To build the Translator first build the vim-adaptor using maven.
```
# build and install vim-adaptor to your maven repository (~/.m2)
cd vim-adaptor; mvn -Dmaven.test.skip=true -q install
cd ..
```
Next build the Translator using maven.
```
# build the Translator
cd sandman/placement
mvn -Dmaven.test.skip=true -q compile assembly:single
cd ../..
```
The result will be `placement-0.0.1-SNAPSHOT-jar-with-dependencies.jar` in `sandman/placement/target`.

## Starting

```
cd sandman/placement/
java -jar target/placement-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```
The Translator will listen to port 8080.

## Configuration

`sandman/placement/defaultConfig` contains the default files.
`sandman/placement/defaultConfig/placementd.yml` is the default configuration file.
The Translator will first search for `placementd.yml` in the `config` folder inside the working directory.
If no file was found it will fallback to the `defaultConfig` folder inside the working directory.

## Docker deployment

Use `Dockerfile` or `docker-compose` to create a new docker container.
The folder `config` is mounted to `/placement/config` and the containing files are being prioritised.

## Documentation

Requirements:
* [Sphinx](http://www.sphinx-doc.org)
* [Javasphinx](https://github.com/bronto/javasphinx)
* [Javadoc](http://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/)
* [LaTeX](https://www.latex-project.org/)

Javasphinx is used to generate the index of java files and enables Sphinx to process the java source code.

```
# Generate Javadoc and Sphinx documentation:
$ cd sandman/doc
$ make
# Remove everything
$ make clean
# Regenerate Javadoc documentation
$ make -B javadoc
# Regenerate Javasphinx java file index
$ make -B javasphinx
# Regenerate Sphinx documentation
$ make -B sphinx/html
$ make -B sphinx/latex
```


