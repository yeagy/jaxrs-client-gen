package io.github.yeagy.jaxrs;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.net.URL;

public class CLITest {

    @Test
    public void testFromJar() throws Exception {
        URL jar = this.getClass().getClassLoader().getResource("example-jaxrs-resource-0.1.jar");
        CLI.main(new String[]{jar.getPath()});
        File file = new File("jaxrs-client-gen/test/SimpleResourceClient.java");
        Assert.assertTrue(file.exists());
        file.delete();
        file.getParentFile().delete();
        file.getParentFile().getParentFile().delete();
    }
}
