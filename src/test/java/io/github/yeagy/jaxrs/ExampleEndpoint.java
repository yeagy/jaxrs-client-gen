package io.github.yeagy.jaxrs;

import javax.ws.rs.*;
import java.util.List;

@Path("example")
@Produces("application/json")
@Consumes("application/json")
public interface ExampleEndpoint {

    @GET
    List<Example> findAll();

    @POST
    void create(Example example);

    @GET
    @Path("{exampleKey}")
    Example find(@PathParam("exampleKey") String exampleKey);

    @PUT
    @Path("{exampleKey}")
    void replace(@PathParam("exampleKey") String exampleKey, Example example);

    @DELETE
    @Path("{exampleKey}")
    void delete(@PathParam("exampleKey") String exampleKey);

    @GET
    @Path("{exampleKey}/text/{subKey}")
    Example findKitchenSink(@PathParam("exampleKey") String exampleKey,
                            @QueryParam("modParam") String modParam,
                            @PathParam("subKey") String subKey,
                            @QueryParam("otherParam") String otherParam);


    @POST
    @Path("{exampleKey}/text/{subKey}")
    Example postKitchenSink(@PathParam("exampleKey") String exampleKey,
                            @QueryParam("modParam") String modParam,
                            @PathParam("subKey") String subKey,
                            @QueryParam("otherParam") String otherParam,
                            Example example);

    @POST
    List<Example> postGenericReturn(Example example);
}
