package io.github.yeagy.jaxrs;

import com.squareup.javapoet.JavaFile;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import javax.ws.rs.Path;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

class CLI {
    @Option(name = "-async", usage = "create an asynchronous client from a class resource")
    private boolean async = false;

    @Argument
    private List<File> paths = new ArrayList<File>();

    public CLI(String[] args) throws CmdLineException {
        CmdLineParser parser = new CmdLineParser(this);
        parser.parseArgument(args);
    }
    
    public static void main(String[] args) throws Exception {
        CLI cli = new CLI(args);
        cli.generate();
    }

    private void generate() throws ClassNotFoundException, IOException {
        ClientGenerator generator = new ClientGenerator(async);
        for (File path : paths) {
            if (path.isDirectory()) {
                List<File> files = new ArrayList<File>();
                findClasses(path, files);
                for (File file : files) {
                    fromFile(generator, file);
                }
            } else {
                fromFile(generator, path);
            }
        }
    }

    private void findClasses(File dir, List<File> classes) {
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".class");
            }
        });
        for (File file : files) {
            classes.add(file);
        }

        File[] subDirs = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        });
        for (File subDir : subDirs) {
            findClasses(subDir, classes);
        }
    }

    private static final String[] COMMON_ROOT_PACKAGES = new String[]{"com", "net", "org", "io"};

    private void fromFile(ClientGenerator generator, File file) throws IOException {
        String fileName = file.getName();
        if (fileName.endsWith(".class")) {
            String path = file.getPath();
            Class<?> klass = null;
            //try to shortcut with common package roots
            for (String root : COMMON_ROOT_PACKAGES) {
                int idx = path.indexOf(root);
                if (idx > 0) {
                    String prefix = path.substring(0, idx - 1);
                    String className = path.substring(idx, path.length() - 6).replace('/', '.');
                    klass = loadClass(prefix, className);
                } else if (idx == 0) {
                    klass = loadClass("", path.replace('/', '.'));
                }
            }
            //try to work backwards through the path
            int idx = path.lastIndexOf('/');
            while (klass == null && idx > 0) {
                String prefix = path.substring(0, idx);
                String className = path.substring(idx + 1, path.length() - 6).replace('/', '.');
                klass = loadClass(prefix, className);
                if (klass == null) {
                    //try again
                    idx = prefix.lastIndexOf('/');
                }
            }
            //maybe we are at the package root
            if (klass == null && idx < 0) {
                klass = loadClass("", path.replace('/', '.'));
            }
            if (klass != null) {
                Path pathAnnotation = klass.getAnnotation(Path.class);
                if (pathAnnotation != null) {
                    JavaFile javaFile = generator.generate(klass);
                    File newFile = new File("jaxrs-client-gen");
                    javaFile.writeTo(newFile);
                }
            }
        }
    }

    private Class<?> loadClass(String path, String className) throws MalformedURLException {
        URLClassLoader classLoader = new URLClassLoader(new URL[]{new File(path).toURI().toURL()});
        try {
            return classLoader.loadClass(className);
        } catch (Throwable e) {
        }
        return null;
    }
}
