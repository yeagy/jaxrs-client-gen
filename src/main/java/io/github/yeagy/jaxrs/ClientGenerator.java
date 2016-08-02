package io.github.yeagy.jaxrs;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

public class ClientGenerator {
    private static final String L_BASE = "base";
    private static final String L_CLIENT = "client";
    private static final String L_ENDPOINT_URL = "endpointUrl";
    private static final String L_ENTITY = "entity";

    private final boolean async;

    public ClientGenerator() {
        this(false);
    }

    public ClientGenerator(boolean async) {
        this.async = async;
    }

    static class ClassData {
        final boolean iface;
        final String className;
        final String path;
        final String[] consumes;
        final String[] produces;
        final List<MethodData> methods;
        final List<ParamData> params;

        ClassData(boolean iface, String className, String path, String[] consumes, String[] produces, List<MethodData> methods, List<ParamData> params) {
            this.iface = iface;
            this.className = className;
            this.path = path;
            this.consumes = consumes;
            this.produces = produces;
            this.methods = methods;
            this.params = params;
        }
    }

    static class MethodData {
        enum Verb {GET, POST, PUT, DELETE}

        final String methodName;
        final Type returnType;
        final String path;
        final String[] consumes;
        final String[] produces;
        final Verb verb;
        final List<ParamData> params;
        final boolean form;

        MethodData(String methodName, Type returnType, String path, String[] consumes, String[] produces, Verb verb, List<ParamData> params) {
            this.methodName = methodName;
            this.returnType = returnType;
            this.path = path;
            this.consumes = consumes;
            this.produces = produces;
            this.verb = verb;
            this.params = params;
            this.form = hasFormParam(params);
        }

        private boolean hasFormParam(List<ParamData> params) {
            for (ParamData param : params) {
                if (param.kind == ParamData.Kind.FORM) {
                    return true;
                } else if (param.kind == ParamData.Kind.BEAN) {
                    return hasFormParam(param.beanParams);
                }
            }
            return false;
        }
    }

    //making this class immutable just makes things ugly...
    static class ParamData {
        enum Kind {PATH, QUERY, MATRIX, FORM, HEADER, COOKIE, BEAN, CONTEXT, ENTITY}

        Class<?> type;
        Type genericType;
        Kind kind;
        String label;
        String call;
        List<ParamData> beanParams = new ArrayList<ParamData>();

        Type[] getGenericTypeArgs() {
            if (genericType != null && genericType instanceof ParameterizedType) {
                return ((ParameterizedType) genericType).getActualTypeArguments();
            }
            return null;
        }
    }

    /**
     * Extracts the JAX-RS metadata from a class via reflection.
     *
     * @param klass JAX-RS resource
     * @return metadata describing a JAX-RS resource
     */
    ClassData analyze(Class klass) {
        List<ParamData> classParamDataList = new ArrayList<ParamData>();
        Method[] methods = klass.getDeclaredMethods();
        Arrays.sort(methods, new Comparator<Method>() {
            @Override
            public int compare(Method l, Method r) {
                return l.getName().compareTo(r.getName());
            }
        });
        List<MethodData> methodDataList = new ArrayList<MethodData>();
        for (Method method : methods) {
            String path = null;
            String[] consumes = null, produces = null;
            MethodData.Verb verb = null;
            for (Annotation annotation : method.getDeclaredAnnotations()) {
                if (annotation instanceof Path) {
                    path = ((Path) annotation).value();
                } else if (annotation instanceof Consumes) {
                    consumes = ((Consumes) annotation).value();
                } else if (annotation instanceof Produces) {
                    produces = ((Produces) annotation).value();
                } else if (annotation instanceof GET) {
                    verb = MethodData.Verb.GET;
                } else if (annotation instanceof POST) {
                    verb = MethodData.Verb.POST;
                } else if (annotation instanceof PUT) {
                    verb = MethodData.Verb.PUT;
                } else if (annotation instanceof DELETE) {
                    verb = MethodData.Verb.DELETE;
                }
            }

            List<ParamData> paramDataList = new ArrayList<ParamData>();
            for (int i = 0; i < method.getParameterTypes().length; i++) {
                ParamData paramData = new ParamData();
                paramData.type = method.getParameterTypes()[i];
                paramData.genericType = method.getGenericParameterTypes()[i];
                handleParamAnnotations(paramData, method.getParameterAnnotations()[i]);
                if (paramData.kind == null) {//todo ensure only single unannotated parameter for entity
                    paramData.kind = ParamData.Kind.ENTITY;
                    paramData.label = L_ENTITY;
                }
                paramData.call = paramData.label;
                paramDataList.add(paramData);
            }

            if (verb == null && method.getName().startsWith("set") && method.getParameterTypes().length == 1) {
                ParamData paramData = new ParamData();
                paramData.type = paramDataList.get(0).type;
                paramData.genericType = paramDataList.get(0).genericType;
                handleParamAnnotations(paramData, method.getDeclaredAnnotations());
                if (paramData.kind != null) {
                    paramData.call = paramData.label;
                    classParamDataList.add(paramData);
                }
            }

            methodDataList.add(new MethodData(method.getName(), method.getGenericReturnType(), path, consumes, produces, verb, paramDataList));
        }

        for (Field field : klass.getDeclaredFields()) {
            ParamData paramData = new ParamData();
            paramData.type = field.getType();
            paramData.genericType = field.getGenericType();
            handleParamAnnotations(paramData, field.getDeclaredAnnotations());
            if (paramData.kind != null) {
                paramData.call = paramData.label;
                classParamDataList.add(paramData);
            }
        }
        for (Constructor constructor : klass.getDeclaredConstructors()) {
            for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                ParamData paramData = new ParamData();
                paramData.type = constructor.getParameterTypes()[i];
                paramData.genericType = constructor.getGenericParameterTypes()[i];
                handleParamAnnotations(paramData, constructor.getParameterAnnotations()[i]);
                if (paramData.kind != null) {
                    paramData.call = paramData.label;
                    classParamDataList.add(paramData);
                }
            }
        }

        Consumes consumes = (Consumes) klass.getAnnotation(Consumes.class);
        Produces produces = (Produces) klass.getAnnotation(Produces.class);
        return new ClassData(klass.isInterface(), klass.getSimpleName(),
                ((Path) klass.getAnnotation(Path.class)).value(),
                consumes != null ? consumes.value() : new String[]{"*/*"},
                produces != null ? produces.value() : new String[]{"*/*"},
                methodDataList, classParamDataList);
    }

    private void handleParamAnnotations(ParamData paramData, Annotation[] annotations) {
        int contextCount = 0;
        int beanCount = 0;
        for (Annotation annotation : annotations) {
            if (annotation instanceof PathParam) {
                paramData.kind = ParamData.Kind.PATH;
                paramData.label = ((PathParam) annotation).value();
            } else if (annotation instanceof QueryParam) {
                paramData.kind = ParamData.Kind.QUERY;
                paramData.label = ((QueryParam) annotation).value();
            } else if (annotation instanceof Context) {
                paramData.kind = ParamData.Kind.CONTEXT;
                paramData.label = ++contextCount == 1 ? "context" : "context" + contextCount;
            } else if (annotation instanceof MatrixParam) {
                paramData.kind = ParamData.Kind.MATRIX;
                paramData.label = ((MatrixParam) annotation).value();
            } else if (annotation instanceof HeaderParam) {
                paramData.kind = ParamData.Kind.HEADER;
                paramData.label = ((HeaderParam) annotation).value();
            } else if (annotation instanceof FormParam) {
                paramData.kind = ParamData.Kind.FORM;
                paramData.label = ((FormParam) annotation).value();
            } else if (annotation instanceof CookieParam) {
                paramData.kind = ParamData.Kind.COOKIE;
                paramData.label = ((CookieParam) annotation).value();
            } else if (annotation instanceof BeanParam) {
                paramData.kind = ParamData.Kind.BEAN;
                paramData.label = ++beanCount == 1 ? "beanParam" : "beanParam" + beanCount;
                paramData.call = paramData.label;
                analyzeBeanParam(paramData);
            }
        }
    }

    private void analyzeBeanParam(ParamData beanParamData) {
        for (Field field : beanParamData.type.getDeclaredFields()) {
            ParamData paramData = new ParamData();
            paramData.type = field.getType();
            paramData.genericType = field.getGenericType();
            handleParamAnnotations(paramData, field.getDeclaredAnnotations());
            if (paramData.kind != null) {
                findGetter(beanParamData, field.getName().toLowerCase(), paramData);
                if (paramData.call == null && !java.lang.reflect.Modifier.isPrivate(field.getModifiers())) {
                    paramData.call = beanParamData.call + "." + paramData.label;
                }
                if (paramData.call != null) {
                    beanParamData.beanParams.add(paramData);
                }
            }
        }
        for (Constructor<?> constructor : beanParamData.type.getDeclaredConstructors()) {
            for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                ParamData paramData = new ParamData();
                paramData.type = constructor.getParameterTypes()[i];
                paramData.genericType = constructor.getGenericParameterTypes()[i];
                handleParamAnnotations(paramData, constructor.getParameterAnnotations()[i]);
                if (paramData.kind != null) {
                    findGetter(beanParamData, paramData.label.toLowerCase(), paramData);
                }
                if (paramData.call == null) {
                    try {
                        Field field = beanParamData.type.getDeclaredField(paramData.label);
                        if (!java.lang.reflect.Modifier.isPrivate(field.getModifiers())) {
                            paramData.call = beanParamData.call + "." + paramData.label;
                        }
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException("bug!", e);
                    }
                }
                if (paramData.call != null) {
                    beanParamData.beanParams.add(paramData);
                }
            }
        }
    }

    private void findGetter(ParamData beanParamData, String nameLower, ParamData paramData) {
        for (Method method : beanParamData.type.getDeclaredMethods()) {
            if (method.getParameterTypes().length == 0
                    && (method.getName().startsWith("get") || method.getName().startsWith("is"))
                    && method.getName().toLowerCase().endsWith(nameLower)) {
                paramData.call = beanParamData.call + "." + method.getName() + "()";
                break;
            }
        }
    }

    /**
     * Generate a JAX-RS resource client via JavaPoet
     *
     * @param klass JAX-RS resource
     * @return JavaPoet java source object
     */
    public JavaFile generate(Class klass) {
        ClassData classData = analyze(klass);
        FieldSpec base = FieldSpec.builder(WebTarget.class, L_BASE, Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Client.class, L_CLIENT)
                .addParameter(String.class, L_ENDPOINT_URL)
                .addStatement("$L = $L.target($L)", L_BASE, L_CLIENT, L_ENDPOINT_URL)
                .build();

        String classNameSuffix = async ? "AsyncClient" : "Client";
        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(classData.className + classNameSuffix)
                .addModifiers(Modifier.PUBLIC)
                .addField(base)
                .addMethod(constructor);

        if (classData.iface) {
            typeSpecBuilder.addSuperinterface(klass);
        }

        for (MethodData methodData : classData.methods) {
            MethodSpec.Builder builder = MethodSpec.methodBuilder(methodData.methodName)
                    .addModifiers(Modifier.PUBLIC);

            if (async && !classData.iface && methodData.verb != null) {
                builder.returns(ParameterizedTypeName.get(Future.class, methodData.returnType));
            } else {
                builder.returns(methodData.returnType);
            }

            if (classData.iface) {
                builder.addAnnotation(Override.class);
            }

            if (methodData.verb == null) {
                if (classData.iface) {
                    int paramCount = 0;
                    for (ParamData paramData : methodData.params) {
                        builder.addParameter(paramData.genericType, "param" + paramCount++);
                    }
                    if (methodData.returnType != void.class) {
                        if (((Class) methodData.returnType).isPrimitive()) {
                            if (methodData.returnType == boolean.class) {
                                builder.addStatement("return false");
                            } else {
                                builder.addStatement("return 0");
                            }
                        } else {
                            builder.addStatement("return null");
                        }
                    }
                    typeSpecBuilder.addMethod(builder.build());
                }
            } else {
                createFormEntity(builder, methodData);
                StringBuilder statement = new StringBuilder();
                pathing(classData.path, classData.params, methodData.path, methodData.params, statement);
                StringBuilder requestParams = new StringBuilder(".request($L)\n");
                params(builder, classData.params, statement, requestParams, classData.iface);
                params(builder, methodData.params, statement, requestParams, classData.iface);
                if (async && !classData.iface) {
                    requestParams.append(".async()\n");
                }
                statement.append(requestParams);
                verb(builder, classData.consumes, classData.produces, methodData, statement.toString());
                typeSpecBuilder.addMethod(builder.build());
            }
        }
        return JavaFile.builder(klass.getPackage().getName(), typeSpecBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();
    }

    private void verb(MethodSpec.Builder builder, String[] classConsumes, String[] classProduces, MethodData methodData, String statement) {
        String[] consumes = methodData.consumes != null ? methodData.consumes : classConsumes;
        String[] produces = methodData.produces != null ? methodData.produces : classProduces;
        String producesString = Arrays.toString(produces).replace(", ", "\", \"").replaceAll("[\\[\\]]", "\"");
        String consumesString = Arrays.toString(consumes).replace(", ", "\", \"").replaceAll("[\\[\\]]", "\"");

        switch (methodData.verb) {
            case GET:
                get(builder, producesString, statement, methodData.returnType);
                break;
            case POST:
                post(builder, consumesString, producesString, statement, methodData.returnType);
                break;
            case PUT:
                put(builder, consumesString, producesString, statement, methodData.returnType);
                break;
            case DELETE:
                delete(builder, producesString, statement, methodData.returnType);
                break;
        }
    }

    private void params(MethodSpec.Builder builder, List<ParamData> params, StringBuilder statement, StringBuilder requestParams, boolean iface) {
        for (ParamData paramData : params) {
            if (iface || paramData.kind != ParamData.Kind.CONTEXT) {
                handleParam(statement, requestParams, paramData);
                builder.addParameter(paramData.genericType, paramData.label);
            }
        }
    }

    private void handleParam(StringBuilder statement, StringBuilder requestParams, ParamData paramData) {
        switch (paramData.kind) {
            case QUERY:
                statement.append(String.format(".queryParam(\"%s\", %s)\n", paramData.label, paramData.call));
                break;
            case MATRIX:
                statement.append(String.format(".matrixParam(\"%s\", %s)\n", paramData.label, paramData.call));
                break;
            case HEADER:
                requestParams.append(String.format(".header(\"%s\", %s)\n", paramData.label, paramData.call));
                break;
            case COOKIE:
                if (paramData.type == Cookie.class) {
                    requestParams.append(String.format(".cookie(%s)\n", paramData.call));
                } else if (paramData.type == String.class) {
                    requestParams.append(String.format(".cookie(\"%s\", %s)\n", paramData.label, paramData.call));
                } else {
                    throw new IllegalArgumentException("cookie parameter type not supported: " + paramData.label);
                }
                break;
            case BEAN:
                for (ParamData beanParamData : paramData.beanParams) {
                    handleParam(statement, requestParams, beanParamData);
                }
                break;
        }
    }

    private void pathing(String classPath, List<ParamData> classParams, String methodPath, List<ParamData> methodParams, StringBuilder statement) {
        Map<String, ParamData> paramByPath = new HashMap<String, ParamData>();
        List<ParamData> params = new ArrayList<ParamData>();
        params.addAll(classParams);
        params.addAll(methodParams);
        for (ParamData param : params) {
            if (param.kind == ParamData.Kind.PATH) {
                paramByPath.put(param.label, param);
            }
            for (ParamData beanParam : param.beanParams) {
                if (beanParam.kind == ParamData.Kind.PATH) {
                    paramByPath.put(beanParam.label, beanParam);
                }
            }
        }

        handlePath(classPath, statement, paramByPath);
        if (methodPath != null) {
            handlePath(methodPath, statement, paramByPath);
        }
    }

    private void handlePath(String path, StringBuilder statement, Map<String, ParamData> paramByPath) {
        for (String part : path.split("/")) {
            if (part.startsWith("{") && part.endsWith("}")) {
                String trimmed = part.substring(1, part.length() - 1);
                if (trimmed.contains(":")) {
                    trimmed = trimmed.substring(0, trimmed.indexOf(':'));
                }
                ParamData paramData = paramByPath.get(trimmed);
                if (paramData != null) {
                    statement.append(String.format(".path(%s)\n", paramData.call));
                } else {
                    throw new IllegalStateException("path param mismatch: " + trimmed);
                }
            } else {
                statement.append(String.format(".path(\"%s\")\n", part));
            }
        }
    }

    private void createFormEntity(MethodSpec.Builder builder, MethodData methodData) {
        if (methodData.form) {
            builder.addStatement("$T<String, String> mmap = new MultivaluedHashMap<String, String>()", MultivaluedHashMap.class);
            for (ParamData paramData : methodData.params) {
                if (paramData.kind == ParamData.Kind.FORM) {
                    if (List.class.isAssignableFrom(paramData.type)) {
                        Type setType = paramData.getGenericTypeArgs()[0];
                        if (setType == String.class) {
                            builder.addStatement("mmap.addAll($S, $L)", paramData.label, paramData.label);
                        } else {
                            builder.beginControlFlow("for ($T $L_i : $L)", setType, paramData.label, paramData.label);
                            builder.addStatement("mmap.add($S, $L_i != null ? $L_i.toString() : null)", paramData.label, paramData.label, paramData.label);
                            builder.endControlFlow();
                        }
                    } else if (Set.class.isAssignableFrom(paramData.type)) {
                        Type setType = paramData.getGenericTypeArgs()[0];
                        if (setType == String.class) {
                            builder.addStatement("mmap.addAll($S, new $T<$T>($L))", paramData.label, ArrayList.class, setType, paramData.label);
                        } else {
                            builder.beginControlFlow("for ($T $L_i : $L)", setType, paramData.label, paramData.label);
                            builder.addStatement("mmap.add($S, $L_i != null ? $L_i.toString() : null)", paramData.label, paramData.label, paramData.label);
                            builder.endControlFlow();
                        }
                    } else if (paramData.type == long.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", paramData.label, Long.class, paramData.label);
                    } else if (paramData.type == int.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", paramData.label, Integer.class, paramData.label);
                    } else if (paramData.type == short.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", paramData.label, Short.class, paramData.label);
                    } else if (paramData.type == double.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", paramData.label, Double.class, paramData.label);
                    } else if (paramData.type == float.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", paramData.label, Float.class, paramData.label);
                    } else if (paramData.type == boolean.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", paramData.label, Boolean.class, paramData.label);
                    } else {
                        if (paramData.type == String.class) {
                            builder.addStatement("mmap.add($S, $L)", paramData.label, paramData.label);
                        } else {
                            builder.addStatement("mmap.add($S, $L != null ? $L.toString() : null)", paramData.label, paramData.label, paramData.label);
                        }
                    }
                }
            }
            builder.addStatement("$T entity = new Form(mmap)", Form.class);
        }
    }

    private void get(MethodSpec.Builder builder, String produces, String statement, Type returnType) {
        if (returnType == void.class) {
            String stmt = String.format("$L%s.get()", statement);
            builder.addStatement(stmt, L_BASE, produces);
        } else if (returnType == Response.class) {
            String stmt = String.format("return $L%s.get()", statement);
            builder.addStatement(stmt, L_BASE, produces);
        } else if (returnType instanceof ParameterizedType) {
            String stmt = String.format("return $L%s.get(new $T<$T>(){})", statement);
            builder.addStatement(stmt, L_BASE, produces, GenericType.class, returnType);
        } else {
            String stmt = String.format("return $L%s.get($T.class)", statement);
            builder.addStatement(stmt, L_BASE, produces, returnType);
        }
    }

    private void post(MethodSpec.Builder builder, String consumes, String produces, String statement, Type returnType) {
        if (returnType == void.class) {
            String stmt = String.format("$L%s.post($T.entity($L, $L))", statement);
            builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes);
        } else if (returnType == Response.class) {
            String stmt = String.format("return $L%s.post($T.entity($L, $L))", statement);
            builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes);
        } else if (returnType instanceof ParameterizedType) {
            String stmt = String.format("return $L%s.post($T.entity($L, $L), new $T<$T>(){})", statement);
            builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes, GenericType.class, returnType);
        } else {
            String stmt = String.format("return $L%s.post($T.entity($L, $L), $T.class)", statement);
            builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes, returnType);
        }
    }

    private void delete(MethodSpec.Builder builder, String produces, String statement, Type returnType) {
        if (returnType == void.class) {
            String stmt = String.format("$L%s.delete()", statement);
            builder.addStatement(stmt, L_BASE, produces);
        } else if (returnType == Response.class) {
            String stmt = String.format("return $L%s.delete()", statement);
            builder.addStatement(stmt, L_BASE, produces);
        } else if (returnType instanceof ParameterizedType) {
            String stmt = String.format("return $L%s.delete(new $T<$T>(){})", statement);
            builder.addStatement(stmt, L_BASE, produces, GenericType.class, returnType);
        } else {
            String stmt = String.format("return $L%s.delete($T.class)", statement);
            builder.addStatement(stmt, L_BASE, produces, returnType);
        }
    }

    private void put(MethodSpec.Builder builder, String consumes, String produces, String statement, Type returnType) {
        if (returnType == void.class) {
            String stmt = String.format("$L%s.put($T.entity($L, $L))", statement);
            builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes);
        } else if (returnType == Response.class) {
            String stmt = String.format("return $L%s.put($T.entity($L, $L))", statement);
            builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes);
        } else if (returnType instanceof ParameterizedType) {
            String stmt = String.format("return $L%s.put($T.entity($L, $L), new $T<$T>(){})", statement);
            builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes, GenericType.class, returnType);
        } else {
            String stmt = String.format("return $L%s.put($T.entity($L, $L), $T.class)", statement);
            builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes, returnType);
        }
    }
}
