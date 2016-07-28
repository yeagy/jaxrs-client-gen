package io.github.yeagy.jaxrs;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
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

public class ClientGenerator {
    private static final String L_BASE = "base";
    private static final String L_CLIENT = "client";
    private static final String L_ENDPOINT_URL = "endpointUrl";
    private static final String L_ENTITY = "entity";

    static class ClassData {
        String className;
        String path;
        String consumes;
        String produces;
        List<MethodData> methods = new ArrayList<MethodData>();
    }

    static class MethodData {
        enum Verb {GET, POST, PUT, DELETE}

        String methodName;
        Verb verb;
        String path;
        String consumes;
        String produces;
        boolean form;
        Type returnType;
        List<ParamData> params = new ArrayList<ParamData>();
    }

    static class ParamData {
        enum Kind {PATH, QUERY, MATRIX, FORM, HEADER, COOKIE, BEAN, CONTEXT, ENTITY}

        Kind kind;
        String label;
        String call;
        Class<?> type;
        Type genericType;
        List<ParamData> beanParams = new ArrayList<ParamData>();

        Type[] getGenericTypeArgs(){
            if (genericType != null && genericType instanceof ParameterizedType) {
                return ((ParameterizedType) genericType).getActualTypeArguments();
            }
            return null;
        }
    }

    public ClassData analyze(Class klass) {
        ClassData classData = new ClassData();
        classData.className = klass.getSimpleName();
        classData.path = ((Path) klass.getAnnotation(Path.class)).value();
        Consumes consumes = (Consumes) klass.getAnnotation(Consumes.class);
        Produces produces = (Produces) klass.getAnnotation(Produces.class);
        classData.consumes = consumes != null ? consumes.value()[0] : "text/plain";
        classData.produces = produces != null ? produces.value()[0] : "text/plain";

        Method[] methods = klass.getDeclaredMethods();
        Arrays.sort(methods, new Comparator<Method>() {
            @Override
            public int compare(Method l, Method r) {
                return l.getName().compareTo(r.getName());
            }
        });
        for (Method method : methods) {
            MethodData methodData = new MethodData();
            classData.methods.add(methodData);
            methodData.methodName = method.getName();
            methodData.returnType = method.getGenericReturnType();

            for (Annotation annotation : method.getDeclaredAnnotations()) {
                if (annotation instanceof Path) {
                    methodData.path = ((Path) annotation).value();
                } else if (annotation instanceof Consumes) {
                    methodData.consumes = ((Consumes) annotation).value()[0];
                } else if (annotation instanceof Produces) {
                    methodData.produces = ((Produces) annotation).value()[0];
                } else if (annotation instanceof GET) {
                    methodData.verb = MethodData.Verb.GET;
                } else if (annotation instanceof POST) {
                    methodData.verb = MethodData.Verb.POST;
                } else if (annotation instanceof PUT) {
                    methodData.verb = MethodData.Verb.PUT;
                } else if (annotation instanceof DELETE) {
                    methodData.verb = MethodData.Verb.DELETE;
                }
            }

            for (int i = 0; i < method.getParameterTypes().length; i++) {
                ParamData paramData = new ParamData();
                methodData.params.add(paramData);
                paramData.type = method.getParameterTypes()[i];
                paramData.genericType = method.getGenericParameterTypes()[i];
                handleParamAnnotations(methodData, paramData, method.getParameterAnnotations()[i]);
                if (paramData.kind == null) {
                    paramData.kind = ParamData.Kind.ENTITY;
                    paramData.label = L_ENTITY;
                }
                paramData.call = paramData.label;
                //todo multiple unrecognized?
            }
        }
        return classData;
    }

    private void handleParamAnnotations(MethodData methodData, ParamData paramData, Annotation[] annotations) {
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
                methodData.form = true;
            } else if (annotation instanceof CookieParam) {
                paramData.kind = ParamData.Kind.COOKIE;
                paramData.label = ((CookieParam) annotation).value();
            } else if (annotation instanceof BeanParam) {
                paramData.kind = ParamData.Kind.BEAN;
                paramData.label = ++beanCount == 1 ? "beanParam" : "beanParam" + beanCount;
                paramData.call = paramData.label;
                analyzeBeanParam(methodData, paramData);
            }
        }
    }

    private void analyzeBeanParam(MethodData methodData, ParamData beanParamData) {
        for (Field field : beanParamData.type.getDeclaredFields()) {
            ParamData paramData = new ParamData();
            handleParamAnnotations(methodData, paramData, field.getDeclaredAnnotations());
            if(paramData.kind != null){
                findGetter(beanParamData, field.getName().toLowerCase(), paramData);
                if(paramData.call == null && !java.lang.reflect.Modifier.isPrivate(field.getModifiers())){
                    paramData.call = beanParamData.call + "." + paramData.label;
                }
                if(paramData.call != null){
                    beanParamData.beanParams.add(paramData);
                    paramData.type = field.getType();
                    paramData.genericType = field.getGenericType();
                }
            }
        }
        for (Constructor<?> constructor : beanParamData.type.getDeclaredConstructors()) {
            for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                ParamData paramData = new ParamData();
                handleParamAnnotations(methodData, paramData, constructor.getParameterAnnotations()[i]);
                if(paramData.kind != null){
                    findGetter(beanParamData, paramData.label.toLowerCase(), paramData);
                }
                if(paramData.call == null){
                    try {
                        Field field = beanParamData.type.getDeclaredField(paramData.label);
                        if(!java.lang.reflect.Modifier.isPrivate(field.getModifiers())){
                            paramData.call = beanParamData.call + "." + paramData.label;
                        }
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();//todo log
                    }
                }
                if(paramData.call != null){
                    beanParamData.beanParams.add(paramData);
                    paramData.type = constructor.getParameterTypes()[i];
                    paramData.genericType = constructor.getGenericParameterTypes()[i];
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

    public JavaFile generate(Class klass) {
        ClassData classData = analyze(klass);
        FieldSpec base = FieldSpec.builder(WebTarget.class, L_BASE, Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Client.class, L_CLIENT)
                .addParameter(String.class, L_ENDPOINT_URL)
                .addStatement("$L = $L.target($L)", L_BASE, L_CLIENT, L_ENDPOINT_URL)
                .build();

        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(classData.className + "Client")
                .addSuperinterface(klass)//todo
                .addModifiers(Modifier.PUBLIC)
                .addField(base)
                .addMethod(constructor);

        for (MethodData methodData : classData.methods) {
            MethodSpec.Builder builder = MethodSpec.methodBuilder(methodData.methodName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(methodData.returnType);

            createFormEntity(builder, methodData);
            StringBuilder statement = new StringBuilder();
            pathing(classData.path, methodData, statement);
            params(builder, methodData, statement);
            verb(builder, classData.consumes, classData.produces, methodData, statement.toString());

            typeSpecBuilder.addMethod(builder.build());
        }
        return JavaFile.builder(klass.getPackage().getName(), typeSpecBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();
    }

    private void verb(MethodSpec.Builder builder, String classConsumes, String classProduces, MethodData methodData, String statement) {
        String consumes = methodData.consumes != null ? methodData.consumes : classConsumes;
        String produces = methodData.produces != null ? methodData.produces : classProduces;
        boolean voidReturn = methodData.returnType == void.class;
        boolean genericReturn = methodData.returnType instanceof ParameterizedType;
        switch (methodData.verb) {
            case GET:
                get(builder, produces, statement, methodData.returnType, genericReturn);
                break;
            case POST:
                post(builder, consumes, produces, statement, methodData.returnType, voidReturn, genericReturn);
                break;
            case PUT:
                put(builder, consumes, produces, statement, methodData.returnType, voidReturn, genericReturn);
                break;
            case DELETE:
                delete(builder, produces, statement, methodData.returnType, voidReturn, genericReturn);
                break;
        }
    }

    private void params(MethodSpec.Builder builder, MethodData methodData, StringBuilder statement) {
        StringBuilder requestParams = new StringBuilder(".request($S)\n");
        for (ParamData paramData : methodData.params) {
            handleParam(statement, requestParams, paramData);
            builder.addParameter(paramData.genericType, paramData.label);
        }
        statement.append(requestParams);
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

    private void pathing(String classPath, MethodData methodData, StringBuilder statement) {
        Map<String, ParamData> paramByPath = new HashMap<String, ParamData>();
        for (ParamData param : methodData.params) {
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
        if (methodData.path != null) {
            handlePath(methodData.path, statement, paramByPath);
        }
    }

    private void handlePath(String path, StringBuilder statement, Map<String, ParamData> paramByPath) {
        for (String part : path.split("/")) {
            if (part.startsWith("{") && part.endsWith("}")) {
                String trimmed = part.substring(1, part.length() - 1);
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

    private void get(MethodSpec.Builder builder, String produces, String statement, Type returnType, boolean genericReturn) {
        if (genericReturn) {
            String stmt = String.format("return $L%s.get(new $T<$T>(){})", statement);
            builder.addStatement(stmt, L_BASE, produces, GenericType.class, returnType);
        } else {
            String stmt = String.format("return $L%s.get($T.class)", statement);
            builder.addStatement(stmt, L_BASE, produces, returnType);
        }
    }

    private void post(MethodSpec.Builder builder, String consumes, String produces, String statement, Type returnType, boolean voidReturn, boolean genericReturn) {
        if (voidReturn) {
            String stmt = String.format("$L%s.post($T.entity($L, $S))", statement);
            builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes);
        } else {
            if (genericReturn) {
                String stmt = String.format("return $L%s.post($T.entity($L, $S), new $T<$T>(){})", statement);
                builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes, GenericType.class, returnType);
            } else {
                String stmt = String.format("return $L%s.post($T.entity($L, $S), $T.class)", statement);
                builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes, returnType);
            }
        }
    }

    private void delete(MethodSpec.Builder builder, String produces, String statement, Type returnType, boolean voidReturn, boolean genericReturn) {
        if (voidReturn) {
            String stmt = String.format("$L%s.delete()", statement);
            builder.addStatement(stmt, L_BASE, produces);
        } else {
            if (genericReturn) {
                String stmt = String.format("return $L%s.delete(new $T<$T>(){})", statement);
                builder.addStatement(stmt, L_BASE, produces, GenericType.class, returnType);
            } else {
                String stmt = String.format("return $L%s.delete($T.class)", statement);
                builder.addStatement(stmt, L_BASE, produces, returnType);
            }
        }
    }

    private void put(MethodSpec.Builder builder, String consumes, String produces, String statement, Type returnType, boolean voidReturn, boolean genericReturn) {
        if (voidReturn) {
            String stmt = String.format("$L%s.put($T.entity($L, $S))", statement);
            builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes);
        } else {
            if (genericReturn) {
                String stmt = String.format("return $L%s.put($T.entity($L, $S), new $T<$T>(){})", statement);
                builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes, GenericType.class, returnType);
            } else {
                String stmt = String.format("return $L%s.put($T.entity($L, $S), $T.class)", statement);
                builder.addStatement(stmt, L_BASE, produces, Entity.class, L_ENTITY, consumes, returnType);
            }
        }
    }
}
