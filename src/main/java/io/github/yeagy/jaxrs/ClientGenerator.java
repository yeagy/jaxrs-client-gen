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
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MultivaluedHashMap;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ClientGenerator {
    private static final String L_BASE = "base";
    private static final String L_CLIENT = "client";
    private static final String L_ENDPOINT_URL = "endpointUrl";
    private static final String L_ENTITY = "entity";

    public JavaFile generate(Class klass) {
        Path path = (Path) klass.getAnnotation(Path.class);
        Consumes consumes = (Consumes) klass.getAnnotation(Consumes.class);
        Produces produces = (Produces) klass.getAnnotation(Produces.class);
        String classConsumes = consumes != null ? consumes.value()[0] : "text/plain";
        String classProduces = produces != null ? produces.value()[0] : "text/plain";

        FieldSpec base = FieldSpec.builder(WebTarget.class, L_BASE, Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Client.class, L_CLIENT)
                .addParameter(String.class, L_ENDPOINT_URL)
                .addStatement("$L = $L.target($L).path($S)", L_BASE, L_CLIENT, L_ENDPOINT_URL, path.value())
                .build();

        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(klass.getSimpleName() + "Client")
                .addSuperinterface(klass)
                .addModifiers(Modifier.PUBLIC)
                .addField(base)
                .addMethod(constructor);

        Method[] methods = klass.getDeclaredMethods();
        Arrays.sort(methods, new Comparator<Method>() {
            @Override
            public int compare(Method l, Method r) {
                return l.getName().compareTo(r.getName());
            }
        });
        for (Method method : methods) {
            MethodSpec.Builder builder = MethodSpec.methodBuilder(method.getName())
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class);

            String methodConsumes = classConsumes;
            if (method.getAnnotation(Consumes.class) != null) {
                methodConsumes = method.getAnnotation(Consumes.class).value()[0];
            }
            String methodProduces = classProduces;
            if (method.getAnnotation(Produces.class) != null) {
                methodProduces = method.getAnnotation(Produces.class).value()[0];
            }

            StringBuilder statement = new StringBuilder();
            pathing(method, statement);//1
            parameters(method, builder, statement);//2
            methods(method, builder, methodConsumes, methodProduces, statement.toString());//3

            typeSpecBuilder.addMethod(builder.build());
        }
        return JavaFile.builder(klass.getPackage().getName(), typeSpecBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();
    }

    private void methods(Method method, MethodSpec.Builder builder, String consumes, String produces, String statement) {
        for (Annotation annotation : method.getDeclaredAnnotations()) {
            Type returnType = method.getReturnType();
            boolean voidReturn = returnType == void.class;
            boolean genericReturn = false;
            if (!voidReturn) {
                Type genericReturnType = method.getGenericReturnType();
                if (genericReturnType instanceof ParameterizedType) {
                    genericReturn = true;
                    returnType = method.getGenericReturnType();
                }
            }
            builder.returns(returnType);

            if (annotation instanceof GET) {
                get(builder, produces, statement, returnType, genericReturn);
            } else if (annotation instanceof POST) {
                post(builder, consumes, produces, statement, returnType, voidReturn, genericReturn);
            } else if (annotation instanceof DELETE) {
                delete(builder, produces, statement, returnType, voidReturn, genericReturn);
            } else if (annotation instanceof PUT) {
                put(builder, consumes, produces, statement, returnType, voidReturn, genericReturn);
            }
        }
    }

    private void parameters(Method method, MethodSpec.Builder builder, StringBuilder statement) {
        boolean createdMultimap = false;
        for (int i = 0; i < method.getParameterTypes().length; i++) {
            Class<?> param = method.getParameterTypes()[i];
            Type paramType = method.getGenericParameterTypes()[i];
            for (Annotation annotation : method.getParameterAnnotations()[i]) {
                if (annotation instanceof FormParam) {
                    String label = ((FormParam) annotation).value();

                    if (!createdMultimap) {//prefix with one of these.
                        builder.addStatement("$T<String, String> mmap = new MultivaluedHashMap<String, String>()", MultivaluedHashMap.class);
                        createdMultimap = true;
                    }

                    if (List.class.isAssignableFrom(param)) {
                        builder.addStatement("mmap.addAll($S, $L)", label, label);
                    } else if (Set.class.isAssignableFrom(param)) {
                        Type setType = ((ParameterizedType) paramType).getActualTypeArguments()[0];
                        if (setType == String.class) {
                            builder.addStatement("mmap.addAll($S, new $T<$T>($L))", label, ArrayList.class, setType, label);
                        } else {
                            builder.beginControlFlow("for ($T $L_i : $L)", setType, label, label);
                            builder.addStatement("mmap.add($S, $L_i != null ? $L_i.toString() : null)", label, label, label);
                            builder.endControlFlow();
                        }
                    } else if (param == long.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", label, Long.class, label);
                    } else if (param == int.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", label, Integer.class, label);
                    } else if (param == short.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", label, Short.class, label);
                    } else if (param == double.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", label, Double.class, label);
                    } else if (param == float.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", label, Float.class, label);
                    } else if (param == boolean.class) {
                        builder.addStatement("mmap.add($S, $T.toString($L))", label, Boolean.class, label);
                    } else {
                        if (param == String.class) {
                            builder.addStatement("mmap.add($S, $L)", label, label);
                        } else {
                            builder.addStatement("mmap.add($S, $L != null ? $L.toString() : null)", label, label, label);
                        }
                    }
                }
            }
        }
        if (createdMultimap) {
            builder.addStatement("$T entity = new Form(mmap)", Form.class);
        }

        StringBuilder requestParams = new StringBuilder(".request($S)\n");
        int contextCount = 1;
        for (int i = 0; i < method.getParameterTypes().length; i++) {
            String label = "param" + i;//should never see this, used for troubleshooting
            Type paramType = method.getGenericParameterTypes()[i];
            boolean entityParam = true;
            for (Annotation annotation : method.getParameterAnnotations()[i]) {
                if (annotation instanceof PathParam) {
                    label = ((PathParam) annotation).value();
                    entityParam = false;
                } else if (annotation instanceof QueryParam) {
                    label = ((QueryParam) annotation).value();
                    statement.append(String.format(".queryParam(\"%s\", %s)\n", label, label));
                    entityParam = false;
                } else if (annotation instanceof Context) {
                    label = contextCount++ == 1 ? "context" : "context" + contextCount;
                    entityParam = false;
                } else if (annotation instanceof MatrixParam) {
                    label = ((MatrixParam) annotation).value();
                    statement.append(String.format(".matrixParam(\"%s\", %s)\n", label, label));
                    entityParam = false;
                } else if (annotation instanceof HeaderParam) {
                    label = ((HeaderParam) annotation).value();
                    requestParams.append(String.format(".header(\"%s\", %s)\n", label, label));
                    entityParam = false;
                } else if (annotation instanceof FormParam) {
                    label = ((FormParam) annotation).value();
                    entityParam = false;
                } else if (annotation instanceof CookieParam) {
                    throw new UnsupportedOperationException("cookie params not yet supported");
                } else if (annotation instanceof BeanParam) {
                    throw new UnsupportedOperationException("bean params not yet supported");
                }
            }
            if (entityParam) {
                label = L_ENTITY;
            }
            builder.addParameter(paramType, label);
        }
        statement.append(requestParams);
    }

    private void pathing(Method method, StringBuilder statement) {
        List<String> pathParams = new ArrayList<String>();
        for (Annotation[] paramAnnotations : method.getParameterAnnotations()) {
            for (Annotation annotation : paramAnnotations) {
                if (annotation instanceof PathParam) {
                    pathParams.add(((PathParam) annotation).value());
                }
            }
        }

        Path subPath = method.getAnnotation(Path.class);
        if (subPath != null) {
            for (String part : subPath.value().split("/")) {
                if (part.startsWith("{") && part.endsWith("}")) {
                    String trimmed = part.substring(1, part.length() - 1);
                    if (pathParams.contains(trimmed)) {
                        statement.append(String.format(".path(%s)\n", trimmed));
                    } else {
                        throw new IllegalStateException("path param mismatch: " + trimmed);
                    }
                } else {
                    statement.append(String.format(".path(\"%s\")\n", part));
                }
            }
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
