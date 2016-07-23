package io.github.yeagy.jaxrs;

import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;

public class ExampleEndpointClient implements ExampleEndpoint {
    private final WebTarget base;

    public ExampleEndpointClient(Client client, String endpointUrl) {
        base = client.target(endpointUrl).path("example");
    }

    @Override
    public void create(Example example) {
        base.request("application/json")
                .post(Entity.entity(example, "application/json"));
    }

    @Override
    public void delete(String exampleKey) {
        base.path(exampleKey)
                .request("application/json")
                .delete();
    }

    @Override
    public Example find(String exampleKey, String modParam) {
        return base.path(exampleKey)
                .queryParam("modParam", modParam)
                .request("application/json")
                .get(Example.class);
    }

    @Override
    public List<Example> findAll() {
        return base.request("application/json")
                .get(new GenericType<List<Example>>(){});
    }

    @Override
    public void replace(String exampleKey, Example example) {
        base.path(exampleKey)
                .request("application/json")
                .put(Entity.entity(example, "application/json"));
    }
}
