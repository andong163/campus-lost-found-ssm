package com.team.cms.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.auth.security.RequirePermission;
import java.beans.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.validation.constraints.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.*;

/** 从当前 Controller/DTO 汇总接口；CSRF 仅标注已接入服务端校验的方法。 */
public final class GmyR3ApiCatalog {
    private final Map<String,Object> schemas=new TreeMap<>();
    private final Map<String,Object> paths=new TreeMap<>();
    private static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result=new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]); return result;
    }
    private static String typeName(Type type) { return type.getTypeName().replaceAll("[^A-Za-z0-9_]","_"); }
    private static Type resolve(Type type,Map<TypeVariable<?>,Type> bindings) {
        return type instanceof TypeVariable<?>?bindings.getOrDefault(type,Object.class):type;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> schema(Type original,Map<TypeVariable<?>,Type> inherited) throws Exception {
        Type type=resolve(original,inherited); Class<?> raw; Map<TypeVariable<?>,Type> bindings=new HashMap<>(inherited);
        if (type instanceof ParameterizedType) {
            ParameterizedType p=(ParameterizedType)type; raw=(Class<?>)p.getRawType();
            if (Collection.class.isAssignableFrom(raw)) return map("type","array","items",schema(p.getActualTypeArguments()[0],inherited));
            if (Map.class.isAssignableFrom(raw)) return map("type","object","additionalProperties",schema(p.getActualTypeArguments()[1],inherited));
            TypeVariable<?>[] variables=raw.getTypeParameters();
            for (int i=0;i<variables.length;i++) bindings.put(variables[i],resolve(p.getActualTypeArguments()[i],inherited));
        } else raw=type instanceof Class<?>?(Class<?>)type:Object.class;
        if (raw==void.class || raw==Void.class || raw==Object.class) return map("nullable",true);
        if (raw==String.class || raw==Character.class) return map("type","string");
        if (raw==boolean.class || raw==Boolean.class) return map("type","boolean");
        if (raw==int.class || raw==Integer.class) return map("type","integer","format","int32");
        if (raw==long.class || raw==Long.class) return map("type","integer","format","int64");
        if (raw==java.time.LocalDateTime.class) return map("type","string","description","本地 ISO 日期时间，Asia/Shanghai，例如 2026-10-29T12:00:00（不带时区偏移）");
        if (raw.isEnum()) return map("type","string","enum",Arrays.stream(raw.getEnumConstants()).map(Object::toString).collect(Collectors.toList()));
        String key=typeName(type); if (schemas.containsKey(key)) return map("$ref","#/components/schemas/"+key);
        Map<String,Object> properties=new LinkedHashMap<>(), definition=map("type","object","properties",properties);
        schemas.put(key,definition); List<String> required=new ArrayList<>();
        for (PropertyDescriptor property:Introspector.getBeanInfo(raw,Object.class).getPropertyDescriptors()) {
            Method getter=property.getReadMethod(); if (getter==null || property.getName().equals("validRange") || property.getName().equals("excludeAuthorId")) continue;
            Map<String,Object> value=new LinkedHashMap<>(schema(getter.getGenericReturnType(),bindings));
            Field field=null; for (Class<?> parent=raw;parent!=null && field==null;parent=parent.getSuperclass()) {
                try { field=parent.getDeclaredField(property.getName()); } catch (NoSuchFieldException ignored) { }
            }
            if (field!=null) {
                if (field.isAnnotationPresent(NotNull.class)||field.isAnnotationPresent(NotBlank.class)||field.isAnnotationPresent(NotEmpty.class)) required.add(property.getName());
                if (field.isAnnotationPresent(NotBlank.class)) value.put("minLength",1);
                if (field.isAnnotationPresent(NotEmpty.class)) value.put("array".equals(value.get("type"))?"minItems":"minLength",1);
                if (field.isAnnotationPresent(Min.class)) value.put("minimum",field.getAnnotation(Min.class).value());
                if (field.isAnnotationPresent(Max.class)) value.put("maximum",field.getAnnotation(Max.class).value());
                if (field.isAnnotationPresent(Positive.class)) value.put("minimum",1);
                if (field.isAnnotationPresent(PositiveOrZero.class)) value.put("minimum",0);
                if (field.isAnnotationPresent(Pattern.class)) value.put("pattern",field.getAnnotation(Pattern.class).regexp());
                if (field.isAnnotationPresent(Size.class)) {
                    Size size=field.getAnnotation(Size.class); boolean array="array".equals(value.get("type"));
                    value.put(array?"minItems":"minLength",size.min()); value.put(array?"maxItems":"maxLength",size.max());
                }
            }
            if (property.getName().equals("password")) value.put("writeOnly",true);
            properties.put(property.getName(),value);
        }
        if (!required.isEmpty()) definition.put("required",required);
        if (raw.getSimpleName().equals("PrivateArticleDetail")) definition.put("description","授权后的私有视图，含联系方式；禁止用于公开列表或公开详情。");
        return map("$ref","#/components/schemas/"+key);
    }
    // 显式记录已核验的入口，避免仅因 URL 前缀相同就将新接口误标为受保护。
    private static final Set<String> CSRF_CHECKED_METHODS = Set.of(
        "ArticleDraftController#createMyDraft", "ArticleDraftController#edit",
        "ArticleDraftController#submit", "ArticleDraftController#withdraw",
        "ArticleDraftController#offline", "ArticleDraftController#toDraft",
        "ArticleManagementController#publish", "ArticleManagementController#offline",
        "ArticleManagementController#delete", "ArticleManagementController#batchPublish",
        "ArticleManagementController#batchOffline", "ArticleManagementController#batchDelete",
        "ReviewController#decide", "ReviewController#approve", "ReviewController#reject",
        "ReviewController#publish", "ClaimController#apply", "ClaimController#decide",
        "ClaimController#cancel", "ClaimController#resolve",
        "UserController#updateStatus", "UserController#assignRoles",
        "RoleController#create", "RoleController#update", "RoleController#assignPermissions");
    public static boolean csrfEnforced(Method method) {
        return CSRF_CHECKED_METHODS.contains(method.getDeclaringClass().getSimpleName()+"#"+method.getName());
    }
    public static Set<Class<?>> controllers() throws Exception {
        Set<Class<?>> classes=new TreeSet<>(Comparator.comparing(Class::getName));
        Path root=Path.of("src/main/java");
        try (var files=Files.walk(root)) {
            for (Path path:files.filter(p->p.toString().endsWith("Controller.java")).collect(Collectors.toList())) {
                String name=root.relativize(path).toString().replace('\\','.').replace('/','.').replaceFirst("\\.java$","");
                Class<?> type=Class.forName(name);
                if (type.isAnnotationPresent(RestController.class)) classes.add(type);
            }
        }
        return classes;
    }
    @SuppressWarnings("unchecked") public Map<String,Object> document() throws Exception {
        for (Class<?> type:controllers()) {
            RequestMapping parent=AnnotatedElementUtils.findMergedAnnotation(type,RequestMapping.class);
            String prefix=parent==null || parent.value().length==0?"":parent.value()[0];
            for (Method method:type.getDeclaredMethods()) {
                RequestMapping mapping=AnnotatedElementUtils.findMergedAnnotation(method,RequestMapping.class);
                if (mapping==null) continue;
                for (String suffix:mapping.value().length==0?new String[]{""}:mapping.value()) {
                    String path=prefix+suffix;
                    for (RequestMethod verb:mapping.method()) {
                        String http=verb.name(); boolean publicRoute=path.startsWith("/api/system/") || path.startsWith("/api/public/")
                            || path.equals("/api/auth/login") || path.equals("/api/auth/register");
                        String owner=type.getName().contains(".auth.")?"郭金滔":type.getName().contains(".category.")?"孙睿":type.getName().contains(".article.")?"袁运轩":type.getName().contains(".system.")?"高鸣远":"杨程";
                        Map<String,Object> operation=map("summary",method.getName(),"operationId",type.getSimpleName()+"_"+method.getName(),
                            "tags",List.of(owner),"x-controller",type.getName()+"#"+method.getName(),"x-csrf-enforced",csrfEnforced(method));
                        RequirePermission permission=method.getAnnotation(RequirePermission.class);
                        operation.put("description",(permission==null?"身份/所有权要求见当前接口约定。":"所需权限："+permission.value()+"，或 ADMIN。")+
                            (csrfEnforced(method)?" 写请求要求同会话 X-CSRF-Token。":" 当前未在此接口执行 CSRF 令牌检查；GET 只读接口不需要写请求令牌。"));
                        operation.put("security",publicRoute?List.of():List.of(map("SessionCookie",List.of())));
                        if (permission!=null) operation.put("x-permission",permission.value());
                        List<Object> parameters=new ArrayList<>(); java.util.regex.Matcher ids=java.util.regex.Pattern.compile("\\{([^}]+)\\}").matcher(path);
                        while(ids.find()) parameters.add(map("name",ids.group(1),"in","path","required",true,"schema",map("type","integer","format","int64")));
                        for (Parameter parameter:method.getParameters()) {
                            if (parameter.isAnnotationPresent(RequestBody.class)) operation.put("requestBody",map("required",true,"content",map("application/json",map("schema",schema(parameter.getParameterizedType(),Map.of())))));
                            if (parameter.isAnnotationPresent(ModelAttribute.class)) {
                                Map<String,Object> model=schema(parameter.getParameterizedType(),Map.of());
                                Map<String,Object> props=(Map<String,Object>)schemas.get(((String)model.get("$ref")).replace("#/components/schemas/",""));
                                for (var property:((Map<String,Object>)props.get("properties")).entrySet())
                                    parameters.add(map("name",property.getKey(),"in","query","required",false,"schema",property.getValue()));
                            }
                        }
                        if (csrfEnforced(method)) parameters.add(map("name","X-CSRF-Token","in","header","required",true,"schema",map("type","string"),"description","登录后 GET /api/auth/csrf 返回的 data 字符串；不写入共享环境或代码"));
                        if (!parameters.isEmpty()) operation.put("parameters",parameters);
                        Map<String,Object> responses=map("200",map("description","成功，HTTP 与 Result.code 均为 200","content",map("application/json",map("schema",schema(method.getGenericReturnType(),Map.of())))));
                        for (String error:List.of("400","401","403","404","409","500","503")) responses.put(error,map("$ref","#/components/responses/Error"));
                        operation.put("responses",responses);
                        ((Map<String,Object>)paths.computeIfAbsent(path,k->new TreeMap<>())).put(http.toLowerCase(Locale.ROOT),operation);
                    }
                }
            }
        }
        schemas.put("ErrorResult",map("type","object","required",List.of("code","message","data"),"properties",map("code",map("type","integer"),"message",map("type","string"),"data",map("nullable",true))));
        return map("openapi","3.0.3","info",map("title","校园失物招领 SSM 主接口","version","0.3.0-gmy-r3",
            "description","汇总第三轮集成源码的实际 Controller/DTO，含公开检索、批量管理与认领。已存在的交接/结案入口仅记录实现，不代表第四轮已验收。角色/所有权及兼容路由说明见 docs/api-contracts-current.md。错误响应列为统一错误契约，不表示每个接口都会产生所有错误。"),
            "servers",List.of(map("url","http://127.0.0.1:8088/cms")),"paths",paths,"components",map("schemas",schemas,
                "securitySchemes",map("SessionCookie",map("type","apiKey","in","cookie","name","JSESSIONID")),
                "responses",map("Error",map("description","统一安全错误，HTTP 状态与 code 一致；无 SQL、堆栈或口令","content",map("application/json",map("schema",map("$ref","#/components/schemas/ErrorResult")))))));
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=1) throw new IllegalArgumentException("Usage: outputPath");
        Path output=Path.of(args[0]); Files.createDirectories(output.toAbsolutePath().getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),new GmyR3ApiCatalog().document());
        System.out.println("已从实际 Controller/DTO 导出主接口文件");
    }
}
