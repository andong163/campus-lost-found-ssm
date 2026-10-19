param(
    [switch]$SourceOnly,
    [string]$WarPath,
    [string]$ReportPath
)
$ErrorActionPreference = 'Stop'
$foundationRoot = Split-Path -Parent $PSScriptRoot
if (-not $WarPath) { $WarPath = Join-Path $foundationRoot 'target/cms.war' }
if (-not $ReportPath) { $ReportPath = Join-Path $foundationRoot 'target/gmy-r1-verification.json' }

[xml]$project = Get-Content -LiteralPath (Join-Path $foundationRoot 'pom.xml') -Raw
$namespaces = New-Object System.Xml.XmlNamespaceManager($project.NameTable)
$namespaces.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
if ($project.SelectSingleNode('/m:project/m:packaging', $namespaces).InnerText -ne 'war') {
    throw 'SSM 基线必须以 WAR 交付。'
}
$javaRelease = $project.SelectSingleNode('/m:project/m:properties/m:maven.compiler.release', $namespaces)
if (-not $javaRelease -or $javaRelease.InnerText -ne '11') { throw 'SSM 基线必须使用 Java 11。' }
foreach ($entry in $project.SelectNodes('//m:dependency | //m:plugin | /m:project/m:parent', $namespaces)) {
    $group = $entry.SelectSingleNode('m:groupId', $namespaces)
    $artifact = $entry.SelectSingleNode('m:artifactId', $namespaces)
    $scope = $entry.SelectSingleNode('m:scope', $namespaces)
    if (($group -and $group.InnerText -eq 'org.springframework.boot') -or
        ($artifact -and $artifact.InnerText -match '(spring-boot|boot.*starter)')) {
        throw 'SSM 基线禁止 Spring Boot 依赖、父 POM、BOM 和插件。'
    }
    if ($group -and $group.InnerText -eq 'org.apache.tomcat.embed' -and
        (-not $scope -or $scope.InnerText -ne 'test')) { throw '嵌入 Tomcat 只能是 test 依赖。' }
}
$mainJava = Join-Path $foundationRoot 'src/main/java'
$forbiddenSource = @(Get-ChildItem -LiteralPath $mainJava -Recurse -Filter '*.java' |
    Select-String -Pattern 'org\.springframework\.boot|@SpringBootApplication|@EnableAutoConfiguration|SpringApplication\s*\.\s*run')
if ($forbiddenSource.Count -ne 0) { throw '业务源码中发现 Spring Boot 导入、注解或入口。' }
$moduleNames = @('auth', 'category', 'items', 'review', 'claims', 'audit')
foreach ($moduleName in $moduleNames) {
    if (-not (Test-Path -LiteralPath (Join-Path $foundationRoot "src/main/webapp/pages/$moduleName") -PathType Container)) {
        throw "模块页面目录缺失：$moduleName"
    }
}
if ($SourceOnly) { Write-Output 'SSM source checked: WAR, Java 11, no Spring Boot, module directories present.'; return }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($WarPath))
$libraries = New-Object 'System.Collections.Generic.List[string]'
try {
    foreach ($entry in $archive.Entries) {
        $entryName = $entry.FullName.Replace('\', '/')
        if ($entryName -match '(^|/)(jdbc-local\.properties|\.env([^/]*)?|\.gitkeep|README\.md|\.git)(/|$)' -or
            $entryName -match '^WEB-INF/classes/com/team/cms/(support|tools)/|(^|/)[^/]*(Test|IT)\.class$') {
            throw "WAR 含私密文件、占位文件、测试类或本地工具：$entryName"
        }
        if ($entryName -notmatch '^WEB-INF/lib/[^/]+\.jar$') { continue }
        $libraries.Add($entryName)
        if ($entryName -match '(spring-boot|boot.*starter|tomcat-embed|junit-|hamcrest-|h2-|spring-test-)') {
            throw "WAR 含禁止的运行依赖：$entryName"
        }
        # Check Maven coordinates too, so renaming a forbidden JAR does not bypass the gate.
        $jarStream = $entry.Open()
        $jar = $null
        try {
            $jar = New-Object System.IO.Compression.ZipArchive($jarStream, [IO.Compression.ZipArchiveMode]::Read)
            foreach ($metadata in $jar.Entries) {
                if ($metadata.FullName -notmatch '^META-INF/maven/.+/pom\.properties$') { continue }
                $reader = New-Object IO.StreamReader($metadata.Open())
                try { $coordinates = $reader.ReadToEnd() } finally { $reader.Dispose() }
                if ($coordinates -match '(?m)^groupId\s*=\s*(org\.springframework\.boot|org\.apache\.tomcat\.embed)\s*$' -or
                    $coordinates -match '(?m)^artifactId\s*=\s*(spring-boot[^\r\n]*|[^\r\n]*boot[^\r\n]*starter[^\r\n]*)\s*$') {
                    throw "WAR 含禁止的 Maven 运行坐标：$entryName"
                }
            }
        } finally {
            if ($null -ne $jar) { $jar.Dispose() }
            $jarStream.Dispose()
        }
    }
    if (-not ($libraries | Where-Object { $_ -match '/spring-webmvc-' }) -or
        -not ($libraries | Where-Object { $_ -match '/mybatis-[0-9]' })) { throw 'WAR 缺少 Spring MVC 或 MyBatis。' }
    foreach ($required in @('pages/index.html', 'assets/css/base.css',
            'WEB-INF/classes/com/team/cms/config/WebAppInitializer.class')) {
        if (-not $archive.GetEntry($required)) { throw "WAR 缺少基线资源：$required" }
    }
} finally { $archive.Dispose() }
$report = [ordered]@{
    task = 'GMY R1'; packaging = 'war'; javaRelease = 11
    springBootSourceMatches = 0; forbiddenRuntimeDependencies = 0
    forbiddenArchiveEntries = 0; modulePageDirectories = $moduleNames
    runtimeJarCount = $libraries.Count
    warSha256 = (Get-FileHash -LiteralPath $WarPath -Algorithm SHA256).Hash.ToLowerInvariant()
    runtimeLibraries = @($libraries)
}
$reportDirectory = Split-Path -Parent ([IO.Path]::GetFullPath($ReportPath))
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ReportPath -Encoding utf8
Write-Output "WAR checked: $($libraries.Count) runtime JARs, no Boot, private files, test runtime or local tools."
