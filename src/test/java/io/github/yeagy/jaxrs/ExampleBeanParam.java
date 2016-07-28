package io.github.yeagy.jaxrs;

import javax.ws.rs.MatrixParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

public class ExampleBeanParam {
    @PathParam("beanKey")
    private String beanPath;

    @QueryParam("fieldQuery")
    public int fieldQuery;

    private final Long constructorMatrix;

    public ExampleBeanParam(@MatrixParam("constructorMatrix") Long constructorMatrix) {
        this.constructorMatrix = constructorMatrix;
    }

    public String getBeanPath() {
        return beanPath;
    }

    public Long getConstructorMatrix() {
        return constructorMatrix;
    }

    public void setBeanPath(String beanPath) {
        this.beanPath = beanPath;
    }
}
