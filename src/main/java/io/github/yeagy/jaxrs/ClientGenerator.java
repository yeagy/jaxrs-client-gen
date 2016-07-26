package io.github.yeagy.jaxrs;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
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
import javax.ws.rs.core.GenericType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ClientGenerator {
    private static final String L_BASE = "base";
    private static final String L_CLIENT = "client";
    private static final String L_ENDPOINT_URL = "endpointUrl";
    private static final String L_ENTITY = "entity";

    public JavaFile generate(Class klass) {
        Path path = (Path) klass.getAnnotation(Path.class);
        Consumes consumesA = (Consumes) klass.getAnnotation(Consumes.class);
        Produces producesA = (Produces) klass.getAnnotation(Produces.class);
        String consumes = consumesA != null ? consumesA.value()[0] : "";
        String produces = producesA != null ? producesA.value()[0] : "";

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

            List<String> pathParams = new ArrayList<String>();
            for (Annotation[] paramAnnotations : method.getParameterAnnotations()) {
                for (Annotation annotation : paramAnnotations) {
                    if (annotation instanceof PathParam) {
                        pathParams.add(((PathParam) annotation).value());
                    }
                }
            }

            String extra = "";
            Path subPath = method.getAnnotation(Path.class);
            if (subPath != null) {
                for (String part : subPath.value().split("/")) {
                    if (part.startsWith("{") && part.endsWith("}")) {
                        String trimmed = part.substring(1, part.length() - 1);
                        if (pathParams.contains(trimmed)) {
                            extra += String.format(".path(%s)\n", trimmed);
                        } else {
                            throw new IllegalStateException("path param mismatch: " + trimmed);
                        }
                    } else {
                        extra += String.format(".path(\"%s\")\n", part);
                    }
                }
            }

            for (int i = 0; i < method.getParameterTypes().length; i++) {
                Class<?> param = method.getParameterTypes()[i];
                boolean entityParam = true;
                for (Annotation annotation : method.getParameterAnnotations()[i]) {
                    if (annotation instanceof PathParam) {
                        builder.addParameter(param, ((PathParam) annotation).value());
                        entityParam = false;
                    } else if (annotation instanceof QueryParam) {
                        String value = ((QueryParam) annotation).value();
                        builder.addParameter(param, value);
                        extra += String.format(".queryParam(\"%s\", %s)\n", value, value);
                        entityParam = false;
                    } else if (annotation instanceof FormParam) {
                        throw new UnsupportedOperationException("form params not yet supported");
                    } else if (annotation instanceof Context) {
                        entityParam = false;
                        builder.addParameter(param, "param" + i);
                    }
                }
                if (entityParam) {
                    builder.addParameter(param, L_ENTITY);
                }
            }

            for (Annotation annotation : method.getDeclaredAnnotations()) {
                Type returnType = method.getReturnType();
                boolean voidReturn = returnType == void.class;
                boolean genericReturn = false;
                if (!voidReturn) {
                    Type genericReturnType = method.getGenericReturnType();
                    if (genericReturnType instanceof ParameterizedType) {
//                        genericType = ((ParameterizedType) genericReturnType).getActualTypeArguments()[0].getTypeName();
                        genericReturn = true;
                        returnType = method.getGenericReturnType();
                    }
                }
                builder.returns(returnType);

                if (annotation instanceof GET) {
                    if (genericReturn) {
                        String stmt = String.format("return $L%s.request($S)\n.get(new $T<$T>(){})", extra);
                        builder.addStatement(stmt, L_BASE, consumes, GenericType.class, returnType);
                    } else {
                        String stmt = String.format("return $L%s.request($S)\n.get($T.class)", extra);
                        builder.addStatement(stmt, L_BASE, consumes, returnType);
                    }
                } else if (annotation instanceof POST) {
                    if (voidReturn) {
                        String stmt = String.format("$L%s.request($S)\n.post($T.entity($L, $S))", extra);
                        builder.addStatement(stmt, L_BASE, consumes, Entity.class, L_ENTITY, produces);
                    } else {
                        if (genericReturn) {
                            String stmt = String.format("return $L%s.request($S)\n.post($T.entity($L, $S), new $T<$T>(){})", extra);
                            builder.addStatement(stmt, L_BASE, consumes, Entity.class, L_ENTITY, produces, GenericType.class, returnType);
                        } else {
                            String stmt = String.format("return $L%s.request($S)\n.post($T.entity($L, $S), $T.class)", extra);
                            builder.addStatement(stmt, L_BASE, consumes, Entity.class, L_ENTITY, produces, returnType);
                        }
                    }
                } else if (annotation instanceof DELETE) {
                    if (voidReturn) {
                        String stmt = String.format("$L%s.request($S)\n.delete()", extra);
                        builder.addStatement(stmt, L_BASE, consumes);
                    } else {
                        if (genericReturn) {
                            String stmt = String.format("return $L%s.request($S)\n.delete(new $T<$T>(){})", extra);
                            builder.addStatement(stmt, L_BASE, consumes, GenericType.class, returnType);
                        } else {
                            String stmt = String.format("return $L%s.request($S)\n.delete($T.class)", extra);
                            builder.addStatement(stmt, L_BASE, consumes, returnType);
                        }
                    }
                } else if (annotation instanceof PUT) {
                    if (voidReturn) {
                        String stmt = String.format("$L%s.request($S)\n.put($T.entity($L, $S))", extra);
                        builder.addStatement(stmt, L_BASE, consumes, Entity.class, L_ENTITY, produces);
                    } else {
                        if (genericReturn) {
                            String stmt = String.format("return $L%s.request($S)\n.put($T.entity($L, $S), new $T<$T>(){})", extra);
                            builder.addStatement(stmt, L_BASE, consumes, Entity.class, L_ENTITY, produces, GenericType.class, returnType);
                        } else {
                            String stmt = String.format("return $L%s.request($S)\n.put($T.entity($L, $S), $T.class)", extra);
                            builder.addStatement(stmt, L_BASE, consumes, Entity.class, L_ENTITY, produces, returnType);
                        }
                    }
                }
            }
            typeSpecBuilder.addMethod(builder.build());
        }
        return JavaFile.builder(klass.getPackage().getName(), typeSpecBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();
    }
}
