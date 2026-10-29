$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$fixtureBase = [IO.Path]::GetFullPath((Join-Path $repositoryRoot '.local/foundation-guard-tests'))
New-Item -ItemType Directory -Path $fixtureBase -Force | Out-Null
$fixtureRoot = Join-Path $fixtureBase ([Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
$realWar = Join-Path $repositoryRoot 'target/cms.war'
if (-not (Test-Path -LiteralPath $realWar)) { throw '先执行 scripts/build.ps1。' }

function Add-FixtureEntry([string]$ArchivePath, [string]$EntryName, [byte[]]$Content) {
    $zip = [IO.Compression.ZipFile]::Open($ArchivePath, [IO.Compression.ZipArchiveMode]::Update)
    try {
        $entry = $zip.CreateEntry($EntryName)
        $stream = $entry.Open()
        try { $stream.Write($Content, 0, $Content.Length) } finally { $stream.Dispose() }
    } finally { $zip.Dispose() }
}

try {
    $nestedPath = Join-Path $fixtureRoot 'renamed.jar'
    $nested = [IO.Compression.ZipFile]::Open($nestedPath, [IO.Compression.ZipArchiveMode]::Create)
    $nested.Dispose()
    Add-FixtureEntry $nestedPath 'META-INF/maven/org.springframework.boot/spring-boot/pom.properties' `
        ([Text.Encoding]::UTF8.GetBytes("groupId=org.springframework.boot`nartifactId=spring-boot`nversion=0`n"))
    $cases = @(
        @{Name='private-config'; Entry='WEB-INF/classes/jdbc-local.properties'; Expected='WAR 含私密文件'; Data=[byte[]]@(0)},
        @{Name='env-file'; Entry='.env.local'; Expected='WAR 含私密文件'; Data=[byte[]]@(0)},
        @{Name='test-tool'; Entry='WEB-INF/classes/com/team/cms/tools/LocalServer.class'; Expected='WAR 含私密文件'; Data=[byte[]]@(0)},
        @{Name='boot-name'; Entry='WEB-INF/lib/spring-boot-0.jar'; Expected='WAR 含禁止的运行依赖'; Data=[byte[]]@(0)},
        @{Name='embedded-runtime'; Entry='WEB-INF/lib/tomcat-embed-core-0.jar'; Expected='WAR 含禁止的运行依赖'; Data=[byte[]]@(0)},
        @{Name='renamed-boot'; Entry='WEB-INF/lib/renamed-innocent.jar'; Expected='WAR 含禁止的 Maven 运行坐标'; Data=[IO.File]::ReadAllBytes($nestedPath)}
    )
    foreach ($case in $cases) {
        $fixture = Join-Path $fixtureRoot ($case.Name + '.war')
        Copy-Item -LiteralPath $realWar -Destination $fixture
        Add-FixtureEntry $fixture $case.Entry $case.Data
        $rejected = $false
        try {
            & "$PSScriptRoot/verify-foundation.ps1" -WarPath $fixture -ReportPath (Join-Path $fixtureRoot 'unexpected-report.json')
        } catch {
            if ($_.Exception.Message -notlike ($case.Expected + '*')) { throw }
            $rejected = $true
        }
        if (-not $rejected) { throw "检查脚本没有拒绝污染包：$($case.Name)" }
        Write-Output "Rejected fixture: $($case.Name)"
    }
    Write-Output "Foundation guard negative checks passed: $($cases.Count)."
} finally {
    # Delete only this run's resolved, verified fixture directory.
    $resolvedFixtureRoot = [IO.Path]::GetFullPath($fixtureRoot)
    if ($resolvedFixtureRoot.StartsWith($fixtureBase + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
    }
}
