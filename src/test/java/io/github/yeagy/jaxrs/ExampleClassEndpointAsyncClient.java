package io.github.yeagy.jaxrs;

import java.util.concurrent.Future;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;

public class ExampleClassEndpointAsyncClient {
    private final WebTarget base;

    public ExampleClassEndpointAsyncClient(Client client, String endpointUrl) {
        base = client.target(endpointUrl);
    }

    public Future<Example> findExample(String classHeader, String classKey, long classQuery, String methodKey) {
        return base.path("class")
                .path(classKey)
                .path("more")
                .path(methodKey)
                .queryParam("classQuery", classQuery)
                .request("application/json", "application/xml")
                .header("classHeader", classHeader)
                .async()
                .get(Example.class);
    }
}
