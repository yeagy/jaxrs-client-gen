package io.github.yeagy.jaxrs;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;

public class ExampleClassEndpointClient {
    private final WebTarget base;

    public ExampleClassEndpointClient(Client client, String endpointUrl) {
        base = client.target(endpointUrl);
    }

    public Example findExample(String classHeader, String classKey, long classQuery, String methodKey) {
        return base.path("class")
                .path(classKey)
                .path("more")
                .path(methodKey)
                .queryParam("classQuery", classQuery)
                .request("application/json", "application/xml")
                .header("classHeader", classHeader)
                .get(Example.class);
    }
}
