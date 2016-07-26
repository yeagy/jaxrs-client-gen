package io.github.yeagy.jaxrs;


import com.squareup.javapoet.JavaFile;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Scanner;

public class ClientGeneratorTest {
    @Test
    public void testGenerate() throws Exception {
        JavaFile file = new ClientGenerator().generate(ExampleEndpoint.class);
        file.writeTo(System.out);

        String control = new Scanner(new File("src/test/java/io/github/yeagy/jaxrs/ExampleEndpointClient.java")).useDelimiter("\\Z").next();
        String content = file.toString().trim();

        Assert.assertEquals(control, content);
    }
}
