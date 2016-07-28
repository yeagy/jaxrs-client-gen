# jaxrs-client-gen
generate JAX-RS client java source files.

#### Known limitations
- Targets Java6, so parameter names cannot be known via reflection. This library will use the JAX-RS annotation values as parameter names instead. 95% of the time, it works every time.
- Sub-resources not supported
- On @BeanParam classes: constructor parameters annotated with @*Param types, the annotation label should match the name of the backing field or getter used to call the method. This is a java 6 limitation. 
```java
class MyBeanParam{
    private String example;
    //the annotation label must match either the public getter/field
    public MyBeanParam(@QueryParam("nonMatchingLabel-FAIL") String example){
        this.example = example;
    }
    public String getExample(){
        return example;
    }
}
```