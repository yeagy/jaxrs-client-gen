[![Build Status](https://travis-ci.org/yeagy/jaxrs-client-gen.svg?branch=master)](https://travis-ci.org/yeagy/jaxrs-client-gen)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.yeagy/jaxrs-client-gen/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.yeagy/jaxrs-client-gen)
[![Javadocs](http://javadoc-badge.appspot.com/io.github.yeagy/jaxrs-client-gen.svg?label=javadocs)](http://javadoc-badge.appspot.com/io.github.yeagy/jaxrs-client-gen)

# jaxrs-client-gen
Generate JAX-RS resource client source files.

Handles both classes and interfaces with JAX-RS resource annotations. Class derived resources can generate both synchronous and **asynchronous** clients.

```xml
<dependency>
  <groupId>io.github.yeagy</groupId>
  <artifactId>jaxrs-client-gen</artifactId>
  <version>0.1.0</version>
</dependency>
```

#####Usage
To use from the command line, use the *-capsule.jar, which is executable:
```bash
java -jar <capsule jar> [-async] [<dir>|<filename>]+
```
The -async flag will generate asynchronous clients from class resources.<br>
A directory will be created named 'jaxrs-client-gen' which will contain all the generated sources.

Here is an example that will generate sources from the test classes of this project:
```bash
java -jar build/libs/jaxrs-client-gen-0.1.0-capsule.jar -async build/classes/test
```
On UNIX like systems the jar is self-executing:
```bash
build/libs/jaxrs-client-gen-0.1.0-capsule.jar -async .
```
Afterwards you can copy the generated sources into your source tree:
```bash
cp -r jaxrs-client-gen/* src/main/java/.
```

#### Known limitations
- Sub-resources not supported.
- Targets Java6, so parameter names cannot be known via reflection. This library will use the JAX-RS annotation values as parameter names instead. 95% of the time, it works every time.
- This library is forgiving with a mismatch of the parameter name and the corresponding annotation value (which typically match). One notable exception: any field setters or constructor parameters annotated with @*Param types, the annotation value should match the name of the backing field or getter used to call the method. This is a Java6 limitation. Note: annotating the backing field directly allows label mismatch. 
```java
class MyBeanParam{
    private String example;
    //any annotation value other than "example" would fail in this case.
    public MyBeanParam(@QueryParam("example") String example){
        this.example = example;
    }
    public String getExample(){
        return example;
    }
}
```
