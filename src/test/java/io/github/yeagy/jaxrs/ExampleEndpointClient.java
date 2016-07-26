package io.github.yeagy.jaxrs;

import java.util.List;
import java.util.Set;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MultivaluedHashMap;

public class ExampleEndpointClient implements ExampleEndpoint {
    private final WebTarget base;

    public ExampleEndpointClient(Client client, String endpointUrl) {
        base = client.target(endpointUrl).path("example");
    }

    @Override
    public void create(Example entity) {
        base.request("application/json")
                .post(Entity.entity(entity, "application/json"));
    }

    @Override
    public void delete(String exampleKey) {
        base.path(exampleKey)
                .request("application/json")
                .delete();
    }

    @Override
    public Example find(String exampleKey) {
        return base.path(exampleKey)
                .request("application/json")
                .get(Example.class);
    }

    @Override
    public List<Example> findAll() {
        return base.request("application/json")
                .get(new GenericType<List<Example>>(){});
    }

    @Override
    public Example findKitchenSink(String exampleKey, String headParam, String modParam, String subKey, String mtxParam, String otherParam, Example context) {
        return base.path(exampleKey)
                .path("text")
                .path(subKey)
                .queryParam("modParam", modParam)
                .matrixParam("mtxParam", mtxParam)
                .queryParam("otherParam", otherParam)
                .request("application/json")
                .header("headParam", headParam)
                .get(Example.class);
    }

    @Override
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
        base.path(exampleKey)
                .request("application/json")
                .post(Entity.entity(entity, "application/x-www-form-urlencoded"));
    }

    @Override
    public List<Example> postGenericReturn(Example entity) {
        return base.request("application/json")
                .post(Entity.entity(entity, "application/json"), new GenericType<List<Example>>(){});
    }

    @Override
    public Example postKitchenSink(String exampleKey, String modParam, String subKey, String otherParam, Example entity) {
        return base.path(exampleKey)
                .path("text")
                .path(subKey)
                .queryParam("modParam", modParam)
                .queryParam("otherParam", otherParam)
                .request("application/json")
                .post(Entity.entity(entity, "application/json"), Example.class);
    }

    @Override
    public void replace(String exampleKey, Example entity) {
        base.path(exampleKey)
                .request("application/json")
                .put(Entity.entity(entity, "application/json"));
    }
}
