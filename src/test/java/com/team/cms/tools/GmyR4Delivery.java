package com.team.cms.tools;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

/** 生成可核对的候选源码包；只读 Git，不提交、不打标签、不发布。 */
public final class GmyR4Delivery {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static String git(Path root,String... args) throws Exception {
        List<String> cmd=new ArrayList<>(List.of("git","-C",root.toString())); cmd.addAll(List.of(args));
        Process process=new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String value=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        if (process.waitFor()!=0) throw new IllegalStateException("Git 只读清单生成失败"); return value;
    }
    public static void validateEntry(String name) {
        String normalized=name.replace('\\','/');
        if (normalized.startsWith("/") || normalized.contains(":") || normalized.contains("\u0000")
                || Arrays.stream(normalized.split("/",-1)).anyMatch(p->p.equals("..") || p.equals(".") || p.isBlank()))
            throw new IllegalArgumentException("归档路径不合法");
        String lower=normalized.toLowerCase(Locale.ROOT), file=lower.substring(lower.lastIndexOf('/')+1);
        for (String part:lower.split("/")) if (Set.of(".git",".cache",".tools",".local","target","dist",".idea").contains(part))
            throw new IllegalArgumentException("归档包含本地缓存或运行数据");
        if (file.equals("jdbc-local.properties") || file.startsWith(".env") || file.endsWith(".pem")
                || file.endsWith(".key") || file.endsWith(".p12") || file.endsWith(".jks") || file.endsWith(".log"))
            throw new IllegalArgumentException("归档包含私密配置或运行记录");
    }
    private static List<String> files(Path root) throws Exception {
        SortedSet<String> selected=new TreeSet<>();
        for (String name:git(root,"ls-files","-z","--cached","--others","--exclude-standard").split("\u0000")) {
            if (name.isBlank()) continue;
            if (!(Set.of("pom.xml","README.md",".gitignore",".gitattributes","LICENSE").contains(name)
                    || name.matches("^(src|scripts|sql|docs|config|\\.mvn)/.*"))) continue;
            validateEntry(name);
            Path file=root.resolve(name).normalize();
            if (!Files.isRegularFile(file)) continue;
            if (!file.toRealPath().startsWith(root.toRealPath())) throw new IllegalStateException("源码路径指向项目外部");
            selected.add(name);
        }
        for (String required:List.of("pom.xml","sql/init.sql","src/main/resources/jdbc.properties","docs/gmy-r4.md"))
            if (!selected.contains(required)) throw new IllegalStateException("交付清单缺少必要文件："+required);
        return new ArrayList<>(selected);
    }
    public static String sha(byte[] bytes) throws Exception {
        StringBuilder out=new StringBuilder(); for (byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x",b)); return out.toString();
    }
    public static String fingerprint(Path root) throws Exception {
        StringBuilder text=new StringBuilder();
        for (String name:files(root)) if (!name.startsWith("docs/") && !name.equals("README.md"))
            text.append(name).append('\0').append(sha(Files.readAllBytes(root.resolve(name)))).append('\n');
        return sha(text.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static void zipEntry(ZipOutputStream zip,String name,byte[] data) throws Exception {
        validateEntry(name); ZipEntry entry=new ZipEntry("campus-lost-found-ssm/"+name);
        entry.setTime(315532800000L); zip.putNextEntry(entry); zip.write(data); zip.closeEntry();
    }
    private static void pack(Path root,Path verification,Path output) throws Exception {
        JsonNode record=JSON.readTree(verification.toFile());
        if (!"GMY R4".equals(record.path("task").asText()) || !record.path("engineeringPassed").asBoolean()
                || !record.path("serverStopped").asBoolean() || !record.path("cleanup").path("databaseDropped").asBoolean()
                || !record.path("upgradeCleanup").path("databaseDropped").asBoolean())
            throw new IllegalStateException("缺少当前第四轮验收与清理记录，不能打包");
        if (!fingerprint(root).equals(record.path("codeFingerprint").asText()))
            throw new IllegalStateException("代码在验收后变化，请重新验收");
        Path war=root.resolve("target/cms.war"); String warHash=sha(Files.readAllBytes(war));
        if (!warHash.equals(record.path("warSha256").asText())) throw new IllegalStateException("WAR 与验收记录不一致");
        output=output.toAbsolutePath().normalize(); Path dist=root.resolve("dist").toAbsolutePath().normalize();
        if (!output.getParent().equals(dist) || Files.exists(output) || Files.exists(dist) && !dist.toRealPath().equals(dist))
            throw new IllegalArgumentException("输出须为项目 dist 下的新目录，不能覆盖已有目录或通过目录联接输出");
        Files.createDirectories(output);
        List<Map<String,Object>> manifest=new ArrayList<>();
        Path source=output.resolve("campus-lost-found-ssm-source-candidate.zip");
        try (ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(source),StandardCharsets.UTF_8)) {
            for (String name:files(root)) {
                byte[] bytes=Files.readAllBytes(root.resolve(name)); zipEntry(zip,name,bytes);
                manifest.add(Map.of("path",name,"sha256",sha(bytes),"bytes",bytes.length));
            }
            zipEntry(zip,"GIT-HISTORY.txt",git(root,"log","--date=iso-strict","--format=%H%nAuthor: %an <%ae>%nDate: %ad%nSubject: %s%n").getBytes(StandardCharsets.UTF_8));
            zipEntry(zip,"WORKTREE-STATUS.txt",git(root,"status","--porcelain=v1").getBytes(StandardCharsets.UTF_8));
            zipEntry(zip,"SOURCE-MANIFEST.json",JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
        }
        Files.copy(war,output.resolve("cms.war")); Files.copy(verification,output.resolve("verification.json"));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("task","GMY R4"); result.put("kind","candidate"); result.put("releaseReady",false);
        result.put("releaseBlockers",List.of("其他四人第四轮尚未合入本候选基线并验收","本候选分支待按协作流程合入 develop 并复验","全组最终页面和隐私性能材料待汇总"));
        result.put("generatedAt",Instant.now().toString()); result.put("baseCommit",git(root,"rev-parse","HEAD").trim());
        result.put("workingTreeDirty",!git(root,"status","--porcelain=v1").isBlank());
        result.put("codeFingerprint",fingerprint(root)); result.put("sourceFiles",manifest.size());
        result.put("sourceZipSha256",sha(Files.readAllBytes(source))); result.put("warSha256",warHash);
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("delivery-manifest.json").toFile(),result);
        Files.writeString(output.resolve("交付说明.md"),"# 高鸣远第四轮候选交付包\n\n本包为本地验收候选版，不是全组最终发布版。\n\n解压源码后先阅读 README.md 和 docs/deployment-r4.md；新库初始化，已有库使用增量升级，切勿用 init.sql 重置旧库。\n\nWAR 部署到外部 Tomcat 9，使用 JDK 11。账号配置由每台机器自行填写，不在本包内。\n\n成员第四轮和正式合并状态见 docs/gmy-r4.md。Git 历史为可读记录，未提交内容已纳入源码及 WORKTREE-STATUS.txt，不将这些内容冒充已提交。\n",StandardCharsets.UTF_8);
        System.out.println("候选交付包："+output);
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of("").toRealPath();
        if (args.length==1 && args[0].equals("fingerprint")) { System.out.println(fingerprint(root)); return; }
        if (args.length==3 && args[0].equals("package")) { pack(root,Path.of(args[1]),Path.of(args[2])); return; }
        throw new IllegalArgumentException("Usage: fingerprint | package verificationJson newDistDirectory");
    }
}
