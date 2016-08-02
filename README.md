# jaxrs-client-gen
Generate JAX-RS resource client source files.

Handles both classes and interfaces with JAX-RS resource annotations. Class derived resources can generate both synchronous and **asynchronous** clients.

#### Known limitations
- Targets Java6, so parameter names cannot be known via reflection. This library will use the JAX-RS annotation values as parameter names instead. 95% of the time, it works every time.
- Sub-resources not supported.
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