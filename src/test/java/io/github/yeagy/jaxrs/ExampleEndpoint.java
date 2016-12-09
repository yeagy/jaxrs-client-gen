package io.github.yeagy.jaxrs;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;

@Path("/example/")
@Produces("application/json")
@Consumes("application/json")
public interface ExampleEndpoint {
    Example nonJaxrsMethod(String key, long value);

    @GET
    List<Example> findAll();

    @POST
    void create(Example example);

    @GET
    @Path("{exampleKey: [a-z]+}")
    Example find(@PathParam("exampleKey") String exampleKey);

    @PUT
    @Path("{exampleKey}")
    void replace(@PathParam("exampleKey") String exampleKey, Example example);

    @DELETE
    @Path("{exampleKey}")
    void delete(@PathParam("exampleKey") String exampleKey);

    @GET
    @Path("/{exampleKey}/text/{subKey}/")
    Example findKitchenSink(@PathParam("exampleKey") String exampleKey,
                            @HeaderParam("headParam") String headParam,
                            @QueryParam("modParam") String modParam,
                            @PathParam("subKey") String subKey,
                            @MatrixParam("mtxParam") String mtxParam,
                            @QueryParam("otherParam") String otherParam,
                            @CookieParam("cookieParam") Cookie cookieParam,
                            @Context Example example);


    @POST
    @Path("{exampleKey}/text/{subKey}")
    Example postKitchenSink(@PathParam("exampleKey") String exampleKey,
                            @QueryParam("modParam") String modParam,
                            @PathParam("subKey") String subKey,
                            @QueryParam("otherParam") String otherParam,
                            Example example);

    @POST
    @Consumes("application/x-www-form-urlencoded")
    @Path("{exampleKey}")
    void postFormParams(@PathParam("exampleKey") String exampleKey,
                        @FormParam("soloParam") String soloParam,
                        @FormParam("longParam") long longParam,
                        @FormParam("integerParam") Integer integerParam,
                        @FormParam("listParams") List<String> listParams,
                        @FormParam("setParams") Set<Integer> setParams);

    @POST
    List<Example> postGenericReturn(Example example);

    @GET
    @Path("{exampleKey}/text/{beanKey}")
    Response findBeanParams(@PathParam("exampleKey") String exampleKey, @BeanParam ExampleBeanParam beanParam);
}
