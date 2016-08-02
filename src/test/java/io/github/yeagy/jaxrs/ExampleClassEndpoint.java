package io.github.yeagy.jaxrs;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

@Path("class/{classKey}")
@Produces({"application/json", "application/xml"})
@Consumes("application/json")
public class ExampleClassEndpoint {
    @PathParam("classKey")
    private String classKey;

    private final long classQuery;

    private String classHeader;

    public ExampleClassEndpoint(@QueryParam("classQuery") long classQuery) {
        this.classQuery = classQuery;
    }

    @GET
    @Path("more/{methodKey}")
    public Example findExample(@PathParam("methodKey") String methodKey, @Context Example example) {
        return null;
    }

    @HeaderParam("classHeader")
    public void setClassHeader(String classHeader) {
        this.classHeader = classHeader;
    }
}
