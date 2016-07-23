package io.github.yeagy.jaxrs;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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
import javax.ws.rs.core.GenericType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;


public class ClientGenerator {
    private static final String L_BASE = "base";
    private static final String L_CLIENT = "client";
    private static final String L_ENDPOINT_URL = "endpointUrl";


    public JavaFile generate(Class klass) {
        Path path = (Path) klass.getAnnotation(Path.class);
        Consumes consumes = (Consumes) klass.getAnnotation(Consumes.class);
        Produces produces = (Produces) klass.getAnnotation(Produces.class);

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
        Arrays.sort(methods, (l, r) -> l.getName().compareTo(r.getName()));
        for (Method method : methods) {
            MethodSpec.Builder builder = MethodSpec.methodBuilder(method.getName())
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class);

            String extra = "";
            Path subPath = method.getAnnotation(Path.class);
            if (subPath != null) {
                String value = subPath.value();
                value = value.substring(1, value.length() - 1);
                extra = String.format(".path(%s)\n", value);
            }

            String pathParamName = null;
            String queryParamName = null;
            String entityParamName = null;
            for (Parameter param : method.getParameters()) {
                if (param.getDeclaredAnnotations().length == 0) {
                    entityParamName = param.getName();
                } else {
                    PathParam pathParam = param.getAnnotation(PathParam.class);
                    if (pathParam != null) {
                        pathParamName = pathParam.value();
                    }
                    QueryParam queryParam = param.getAnnotation(QueryParam.class);
                    if (queryParam != null) {
                        queryParamName = queryParam.value();
                        extra += String.format(".queryParam(\"%s\", %s)\n", queryParamName, queryParamName);
                    }
                }
                builder.addParameter(param.getType(), param.getName());
            }

            for (Annotation annotation : method.getDeclaredAnnotations()) {
                if (annotation instanceof GET) {
                    boolean genericType = false;
                    Type genericReturnType = method.getGenericReturnType();
                    if (genericReturnType instanceof ParameterizedType) {
//                        genericType = ((ParameterizedType) genericReturnType).getActualTypeArguments()[0].getTypeName();
                        genericType = true;
                    }

                    if (genericType) {
                        builder.returns(method.getGenericReturnType());
                        String stmt = String.format("return $L%s.request($S)\n.get(new $T<$T>(){})", extra);
                        builder.addStatement(stmt, L_BASE, consumes.value()[0], GenericType.class, method.getGenericReturnType());
                    } else {
                        builder.returns(method.getReturnType());
                        String stmt = String.format("return $L%s.request($S)\n.get($T.class)", extra);
                        builder.addStatement(stmt, L_BASE, consumes.value()[0], method.getGenericReturnType());
                    }
                } else if (annotation instanceof POST) {
                    builder.returns(method.getReturnType());
                    builder.addStatement("$L.request($S)\n.post($T.entity($L, $S))", L_BASE, consumes.value()[0], Entity.class, entityParamName, produces.value()[0]);
                } else if (annotation instanceof DELETE) {
                    builder.returns(method.getReturnType());
                    String stmt = String.format("$L%s.request($S)\n.delete()", extra);
                    builder.addStatement(stmt, L_BASE, consumes.value()[0]);
                } else if (annotation instanceof PUT) {
                    builder.returns(method.getReturnType());
                    String stmt = String.format("$L%s.request($S)\n.put($T.entity($L, $S))", extra);
                    builder.addStatement(stmt, L_BASE, consumes.value()[0], Entity.class, entityParamName, produces.value()[0]);
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
