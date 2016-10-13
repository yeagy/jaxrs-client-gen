package io.github.yeagy.jaxrs;

import org.kohsuke.args4j.Argument;
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
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

class CLI {
    private static class Args {
        @Option(name = "-async", usage = "Create an asynchronous client from a class resource")
        private boolean async = false;

        @Option(name = "-cp", usage = "Semicolon separated classpath entries. Same rules as java. Can be a directory, jar, or wildcard for dir of jars (not recursively searched).")
        private String classpath = null;

        @Argument(required = true, usage = "Space separated paths. Can be a class file, a jar, or a directory (recursively searched).")
        private List<File> paths = new ArrayList<File>();
    }

    private static final String[] COMMON_ROOT_PACKAGES = new String[]{"com", "net", "org", "io"};
    private static final File OUTPUT_DIR = new File("jaxrs-client-gen");

    private final Args args;
    private final ClientGenerator generator;
    private final ClassLoader parentLoader;

    private CLI(Args args) throws MalformedURLException {
        this.args = args;
        generator = new ClientGenerator(args.async);
        parentLoader = getParentClassloader();
    }

    private ClassLoader getParentClassloader() throws MalformedURLException {
        if (args.classpath != null) {
            List<URL> urls = new ArrayList<URL>();
            for (String cp : args.classpath.split(";")) {
                if (cp.endsWith("*")) {
                    File dir = new File(cp.substring(0, cp.length() - 1));
                    File[] jars = dir.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.endsWith(".jar");
                        }
                    });
                    if (jars != null) {
                        for (File jar : jars) {
                            urls.add(jar.toURI().toURL());
                        }
                    }
                } else {
                    urls.add(new File(cp).toURI().toURL());
                }
            }
            return new URLClassLoader(urls.toArray(new URL[urls.size()]), getClass().getClassLoader());
        }
        return getClass().getClassLoader();
    }

    public static void main(String[] input) throws Exception {
        Args args = new Args();
        CmdLineParser parser = new CmdLineParser(args);
        parser.parseArgument(input);
        CLI cli = new CLI(args);
        cli.generate();
    }

    private void generate() throws ClassNotFoundException, IOException {
        for (File path : args.paths) {
            if (path.isDirectory()) {
                writeClasses(path);
            } else {
                String fileName = path.getName();
                if (fileName.endsWith(".jar")) {
                    writeJarClasses(path);
                } else {
                    Class<?> klass = loadClassFromFile(path);
                    if (klass != null) {
                        writeClass(klass);
                    }
                }
            }
        }
    }

    private void writeClass(Class<?> klass) throws IOException {
        if (klass.getAnnotation(Path.class) != null) {
            generator.generate(klass, OUTPUT_DIR);
        }
    }

    private void writeClasses(File dir) throws IOException {
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".class");
            }
        });
        if (files != null) {
            for (File file : files) {
                Class<?> klass = loadClassFromFile(file);
                if (klass != null) {
                    writeClass(klass);
                }
            }
        }

        File[] subDirs = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        });
        if (subDirs != null) {
            for (File subDir : subDirs) {
                writeClasses(subDir);
            }
        }
    }

    private String lastPackageDir = null;

    //all this just to figure out the canonical class name...
    private Class<?> loadClassFromFile(File file) throws MalformedURLException {
        if (file.getName().endsWith(".class")) {
            String path = file.getPath();
            if (lastPackageDir != null) {
                int idx = path.indexOf(lastPackageDir);
                if (idx == 0) {
                    Class<?> klass = loadClass(lastPackageDir, path.substring(lastPackageDir.length()));
                    if (klass != null) {
                        return klass;
                    }
                }
            }
            //try to shortcut with common package roots
            for (String root : COMMON_ROOT_PACKAGES) {
                int idx = path.indexOf(root);
                if (idx > 0) {
                    String packageDir = path.substring(0, idx - 1);
                    String className = path.substring(idx, path.length() - 6).replace('/', '.');
                    Class<?> klass = loadClass(packageDir, className);
                    if (klass != null) {
                        lastPackageDir = packageDir;
                        return klass;
                    }
                } else if (idx == 0) {
                    Class<?> klass = loadClass(".", path.substring(0, path.length() - 6).replace('/', '.'));
                    if (klass != null) {
                        lastPackageDir = ".";
                        return klass;
                    }
                }
            }
            //try to work backwards through the path
            int idx = path.lastIndexOf('/');
            while (idx > 0) {
                String packageDir = path.substring(0, idx);
                String className = path.substring(idx + 1, path.length() - 6).replace('/', '.');
                Class<?> klass = loadClass(packageDir, className);
                if (klass != null) {
                    lastPackageDir = packageDir;
                    return klass;
                } else {
                    //try again
                    idx = packageDir.lastIndexOf('/');
                }
            }
            //maybe we are at the package root
            if (idx < 0) {
                return loadClass(".", path.substring(0, path.length() - 6).replace('/', '.'));
            }
        }
        return null;
    }

    //support just top level jars for now. worried about recursive performance.
    private void writeJarClasses(File jarFile) throws IOException {
        JarFile jar = new JarFile(jarFile);
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String fileName = entry.getName();
            if (fileName.endsWith(".class")) {
                Class<?> klass = loadClass(jarFile, fileName.replace('/', '.').substring(0, fileName.length() - 6));
                if (klass != null) {
                    writeClass(klass);
                }
            }
        }
    }

    private Class<?> loadClass(String directoryOrJarPath, String fullClassName) throws MalformedURLException {
        return loadClass(new File(directoryOrJarPath), fullClassName);
    }

    private Class<?> loadClass(File directoryOrJar, String fullClassName) throws MalformedURLException {
        URLClassLoader classLoader = new URLClassLoader(new URL[]{directoryOrJar.toURI().toURL()}, parentLoader);
        try {
            return classLoader.loadClass(fullClassName);
        } catch (Throwable e) {
        }
        return null;
    }
}
