package io.github.yeagy.jaxrs;


import com.squareup.javapoet.JavaFile;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.Scanner;

public class ClientGeneratorTest {
    @Test
    public void testFromInterface() throws Exception {
        JavaFile file = new ClientGenerator().generate(ExampleEndpoint.class);
        file.writeTo(System.out);

        String control = new Scanner(new File("src/test/java/io/github/yeagy/jaxrs/ExampleEndpointClient.java")).useDelimiter("\\Z").next();
        String content = file.toString().trim();

        Assert.assertEquals(control, content);
    }

    @Test
    public void testFromClass() throws Exception {
        JavaFile file = new ClientGenerator().generate(ExampleClassEndpoint.class);
        file.writeTo(System.out);

        String control = new Scanner(new File("src/test/java/io/github/yeagy/jaxrs/ExampleClassEndpointClient.java")).useDelimiter("\\Z").next();
        String content = file.toString().trim();

        Assert.assertEquals(control, content);
    }

    @Ignore
    @Test
    public void testAsyncFromClass() throws Exception {
        JavaFile file = new ClientGenerator(true).generate(ExampleClassEndpoint.class);
        file.writeTo(System.out);

        String control = new Scanner(new File("src/test/java/io/github/yeagy/jaxrs/ExampleClassEndpointAsyncClient.java")).useDelimiter("\\Z").next();
        String content = file.toString().trim();

        Assert.assertEquals(control, content);
    }
}
