$ErrorActionPreference = 'Stop'
$script:CmsRoot = Split-Path -Parent $PSScriptRoot

function Resolve-CmsJava([string]$JavaHome) {
    if (-not $JavaHome) {
        $portable = Get-ChildItem -LiteralPath (Join-Path $script:CmsRoot '.tools') -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like 'jdk-11*' } | Select-Object -First 1
        if ($portable) { $JavaHome = $portable.FullName } else { $JavaHome = $env:JAVA_HOME }
    }
    if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
        throw '请安装 JDK 11，并用 -JavaHome 指定目录或设置 JAVA_HOME。'
    }
    $release = Get-Content -LiteralPath (Join-Path $JavaHome 'release') -Raw
    if ($release -notmatch 'JAVA_VERSION="11[.\"]') { throw '课程工程使用 JDK 11；请指定 Java 11 目录。' }
    return $JavaHome
}

function Invoke-CmsMaven([string]$JavaHome, [string[]]$Arguments) {
    $maven = (Get-Command mvn.cmd -ErrorAction Stop).Source
    $previousJava = $env:JAVA_HOME
    $previousPath = $env:Path
    $previousOptions = $env:MAVEN_OPTS
    Push-Location $script:CmsRoot
    try {
        $env:JAVA_HOME = Resolve-CmsJava $JavaHome
        $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $previousPath
        $env:MAVEN_OPTS = ($previousOptions + ' -Dfile.encoding=UTF-8').Trim()
        & $maven -s .mvn/settings.xml "-Dmaven.repo.local=$script:CmsRoot\.cache\m2" -B -ntp @Arguments
        if ($LASTEXITCODE -ne 0) { throw 'Maven 执行失败，请查看上方输出。' }
    } finally {
        Pop-Location
        $env:JAVA_HOME = $previousJava
        $env:Path = $previousPath
        $env:MAVEN_OPTS = $previousOptions
    }
}

function Get-CmsToolClasspath([string]$JavaHome, [switch]$ContainerOnly) {
    Invoke-CmsMaven $JavaHome @('dependency:build-classpath', '-Dmdep.includeScope=test', '-Dmdep.outputFile=target/tool-classpath.txt') | Out-Host
    $destination = Join-Path $script:CmsRoot 'target/dev-tools/com/team/cms/tools'
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    foreach ($name in @('LocalServer.class', 'MySqlBootstrap.class')) {
        Copy-Item -LiteralPath (Join-Path $script:CmsRoot "target/test-classes/com/team/cms/tools/$name") -Destination $destination -Force
    }
    # Only command-line tool classes are visible to the parent; fixtures stay out of the deployed WAR.
    $libraries = (Get-Content -LiteralPath (Join-Path $script:CmsRoot 'target/tool-classpath.txt') -Raw).Trim()
    if ($ContainerOnly) {
        $libraries = (($libraries -split ';') | Where-Object { $_ -match '(tomcat-embed-core|tomcat-annotations-api)[^;]*\.jar$' }) -join ';'
        if (-not $libraries) { throw '未找到本地 Tomcat 9 依赖。' }
    }
    return (Join-Path $script:CmsRoot 'target/dev-tools') + ';' + $libraries
}
