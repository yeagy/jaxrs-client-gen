package io.github.yeagy.jaxrs;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

public class ExampleEndpointAsyncClient {
    private final WebTarget base;

    public ExampleEndpointAsyncClient(Client client, String endpointUrl) {
        base = client.target(endpointUrl);
    }

    public void create(Example entity) {
        base.path("example")
                .request("application/json")
                .async()
                .post(Entity.entity(entity, "application/json"));
    }

    public void delete(String exampleKey) {
        base.path("example")
                .path(exampleKey)
                .request("application/json")
                .async()
                .delete();
    }

    public Future<Example> find(String exampleKey) {
        return base.path("example")
                .path(exampleKey)
                .request("application/json")
                .async()
                .get(Example.class);
    }

    public Future<List<Example>> findAll() {
        return base.path("example")
                .request("application/json")
                .async()
                .get(new GenericType<List<Example>>(){});
    }

    public Future<Response> findBeanParams(String exampleKey, ExampleBeanParam beanParam) {
        return base.path("example")
                .path(exampleKey)
                .path("text")
                .path(beanParam.getBeanPath())
                .queryParam("fieldQuery", beanParam.fieldQuery)
                .matrixParam("constructorMatrix", beanParam.getConstructorMatrix())
                .request("application/json")
                .async()
                .get();
    }

    public Future<Example> findKitchenSink(String exampleKey, String headParam, String modParam, String subKey, String mtxParam, String otherParam, Cookie cookieParam, Example context) {
        return base.path("example")
                .path(exampleKey)
                .path("text")
                .path(subKey)
                .queryParam("modParam", modParam)
                .matrixParam("mtxParam", mtxParam)
                .queryParam("otherParam", otherParam)
                .request("application/json")
                .header("headParam", headParam)
                .cookie(cookieParam)
                .async()
                .get(Example.class);
    }

    public Example nonJaxrsMethod(String param0, long param1) {
        return null;
    }

    public void postFormParams(String exampleKey, String soloParam, long longParam, Integer integerParam, List<String> listParams, Set<Integer> setParams) {
        MultivaluedHashMap<String, String> mmap = new MultivaluedHashMap<String, String>();
        mmap.add("soloParam", soloParam);
        mmap.add("longParam", Long.toString(longParam));
        mmap.add("integerParam", integerParam != null ? integerParam.toString() : null);
        mmap.addAll("listParams", listParams);
        for (Integer setParams_i : setParams) {
            mmap.add("setParams", setParams_i != null ? setParams_i.toString() : null);
        }
        Form entity = new Form(mmap);
        base.path("example")
                .path(exampleKey)
                .request("application/json")
                .async()
                .post(Entity.entity(entity, "application/x-www-form-urlencoded"));
    }

    public Future<List<Example>> postGenericReturn(Example entity) {
        return base.path("example")
                .request("application/json")
                .async()
                .post(Entity.entity(entity, "application/json"), new GenericType<List<Example>>(){});
    }

    public Future<Example> postKitchenSink(String exampleKey, String modParam, String subKey, String otherParam, Example entity) {
        return base.path("example")
                .path(exampleKey)
                .path("text")
                .path(subKey)
                .queryParam("modParam", modParam)
                .queryParam("otherParam", otherParam)
                .request("application/json")
                .async()
                .post(Entity.entity(entity, "application/json"), Example.class);
    }

    public void replace(String exampleKey, Example entity) {
        base.path("example")
                .path(exampleKey)
                .request("application/json")
                .async()
                .put(Entity.entity(entity, "application/json"));
    }
}
