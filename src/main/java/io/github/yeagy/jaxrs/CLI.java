package io.github.yeagy.jaxrs;

import com.squareup.javapoet.JavaFile;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import javax.ws.rs.Path;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

class CLI {
    @Option(name = "-async", usage = "create an asynchronous client from a class resource")
    private boolean async = false;

    @Argument(required = true, usage = "paths of classes, space separated. Can be a class file, a jar, or a directory (recursively searched).")
    private List<File> paths = new ArrayList<File>();

    private final ClientGenerator generator;

    public CLI(String[] args) throws CmdLineException {
        CmdLineParser parser = new CmdLineParser(this);
        parser.parseArgument(args);
        generator = new ClientGenerator(async);
    }

    public static void main(String[] args) throws Exception {
        CLI cli = new CLI(args);
        cli.generate();
    }

    private void generate() throws ClassNotFoundException, IOException {
        for (File path : paths) {
            if (path.isDirectory()) {
                List<File> files = new ArrayList<File>();
                findClasses(path, files);
                for (File file : files) {
                    fromFile(file);
                }
            } else {
                String fileName = path.getName();
                if(fileName.endsWith(".jar")){
                    fromJar(path);//support just top level jars for now. worried about recursive performance.
                } else {
                    fromFile(path);
                }
            }
        }
    }

    private void fromJar(File jarFile) throws IOException {
        JarFile jar = new JarFile(jarFile);
        Enumeration<JarEntry> entries = jar.entries();
        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String fileName = entry.getName();
            if (fileName.endsWith(".class")) {
                int idx = fileName.lastIndexOf("/");
                String path = fileName.substring(0, idx);
                String className = fileName.substring(idx + 1, fileName.length());
                File temp = new File(path, className);
                File parentDir = temp.getParentFile();
                if(!parentDir.exists()){
                    parentDir.mkdirs();
                    parentDir.deleteOnExit();
                }
                temp.deleteOnExit();
                InputStream in = jar.getInputStream(entry);
                FileOutputStream out = new FileOutputStream(temp);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.close();
                in.close();
                Class<?> klass = loadClass(".", fileName.substring(0, fileName.length() - 6).replace('/', '.'));
                if (klass != null) {
                    writeClass(klass);
                }
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
        if (files != null) {
            Collections.addAll(classes, files);
        }

        File[] subDirs = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        });
        if (subDirs != null) {
            for (File subDir : subDirs) {
                findClasses(subDir, classes);
            }
        }
    }

    private static final String[] COMMON_ROOT_PACKAGES = new String[]{"com", "net", "org", "io"};

    private void fromFile(File file) throws IOException {
        String fileName = file.getName();
        if (fileName.endsWith(".class")) {
            String path = file.getPath();
            Class<?> klass = null;
            //try to shortcut with common package roots
            for (String root : COMMON_ROOT_PACKAGES) {
                int idx = path.indexOf(root);
                if (idx > 0) {
                    String packageDir = path.substring(0, idx - 1);
                    String className = path.substring(idx, path.length() - 6).replace('/', '.');
                    klass = loadClass(packageDir, className);
                    if (klass != null) {
                        break;
                    }
                } else if (idx == 0) {
                    klass = loadClass(".", path.substring(0, path.length() - 6).replace('/', '.'));
                    if (klass != null) {
                        break;
                    }
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
                klass = loadClass(".", path.substring(0, path.length() - 6).replace('/', '.'));
            }
            if (klass != null) {
                writeClass(klass);
            }
        }
    }


    private void writeClass(Class<?> klass) throws IOException {
        Path pathAnnotation = klass.getAnnotation(Path.class);
        if (pathAnnotation != null) {
            JavaFile javaFile = generator.generate(klass);
            File newFile = new File("jaxrs-client-gen");
            javaFile.writeTo(newFile);
        }
    }

    private Class<?> loadClass(String packageDir, String fullClassName) throws MalformedURLException {
        URLClassLoader classLoader = new URLClassLoader(new URL[]{new File(packageDir).toURI().toURL()});
        try {
            return classLoader.loadClass(fullClassName);
        } catch (Throwable e) {
        }
        return null;
    }
}
