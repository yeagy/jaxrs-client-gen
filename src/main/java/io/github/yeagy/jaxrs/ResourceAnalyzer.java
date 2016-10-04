package io.github.yeagy.jaxrs;

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
import javax.ws.rs.core.Context;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ResourceAnalyzer {
    static final String ENTITY = "entity";

    /**
     * Extracts the JAX-RS metadata from a class via reflection.
     *
     * @param klass JAX-RS resource
     * @return metadata describing a JAX-RS resource
     */
    public ClassData analyze(Class klass) {
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
                    paramData.label = ENTITY;
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

    public static class ClassData {
        public final boolean iface;
        public final String className;
        public final String path;
        public final String[] consumes;
        public final String[] produces;
        public final List<MethodData> methods;
        public final List<ParamData> params;

        public ClassData(boolean iface, String className, String path, String[] consumes, String[] produces, List<MethodData> methods, List<ParamData> params) {
            this.iface = iface;
            this.className = className;
            this.path = path;
            this.consumes = consumes;
            this.produces = produces;
            this.methods = methods;
            this.params = params;
        }
    }

    public static class MethodData {
        public enum Verb {GET, POST, PUT, DELETE}

        public final String methodName;
        public final Type returnType;
        public final String path;
        public final String[] consumes;
        public final String[] produces;
        public final Verb verb;
        public final List<ParamData> params;
        public final boolean form;

        public MethodData(String methodName, Type returnType, String path, String[] consumes, String[] produces, Verb verb, List<ParamData> params) {
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
    public static class ParamData {
        public enum Kind {PATH, QUERY, MATRIX, FORM, HEADER, COOKIE, BEAN, CONTEXT, ENTITY}

        public Class<?> type;
        public Type genericType;
        public Kind kind;
        public String label;
        public String call;
        public List<ParamData> beanParams = new ArrayList<ParamData>();

        public Type[] getGenericTypeArgs() {
            if (genericType != null && genericType instanceof ParameterizedType) {
                return ((ParameterizedType) genericType).getActualTypeArguments();
            }
            return null;
        }
    }
}
