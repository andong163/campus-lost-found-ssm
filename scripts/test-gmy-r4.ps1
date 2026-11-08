param([string]$TomcatHome, [string]$JavaHome, [ValidateRange(1024,65535)][int]$Port = 8098, [switch]$SkipBuild)
. "$PSScriptRoot/common.ps1"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jdk=Resolve-CmsJava $JavaHome
if (-not $TomcatHome) {
    $candidate=Get-ChildItem -LiteralPath (Join-Path $script:CmsRoot '.tools') -Directory |
        Where-Object { $_.Name -match '^apache-tomcat-(9\.0\.\d+)$' } |
        Sort-Object { [version]($_.Name -replace '^apache-tomcat-','') } -Descending | Select-Object -First 1
    if (-not $candidate) { throw '未找到外部 Tomcat 9；先执行 ./scripts/install-tomcat9.ps1 或传入 -TomcatHome。' }
    $TomcatHome=$candidate.FullName
}
$TomcatHome=(Resolve-Path -LiteralPath $TomcatHome).Path
$catalina=Join-Path $TomcatHome 'lib/catalina.jar'
$jar=[IO.Compression.ZipFile]::OpenRead($catalina)
try {
    $entry=$jar.GetEntry('org/apache/catalina/util/ServerInfo.properties')
    $reader=[IO.StreamReader]::new($entry.Open())
    try { $serverInfo=$reader.ReadToEnd() } finally { $reader.Dispose() }
} finally { $jar.Dispose() }
if ($serverInfo -notmatch 'server.info=Apache Tomcat/(9\.0\.\d+)') { throw '外部容器必须为 Tomcat 9.0.x，不能使用 Tomcat 10/11。' }
$serverVersion=$Matches[1]
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$Port)
try { $listener.Start() } finally { $listener.Stop() }
Push-Location $script:CmsRoot
try {
    if (-not $SkipBuild) { & "$PSScriptRoot/build.ps1" -JavaHome $jdk }
    & "$PSScriptRoot/test-shared-ui.ps1"
    & "$PSScriptRoot/test-foundation-checks.ps1"
    & "$PSScriptRoot/verify-foundation.ps1"
    Invoke-CmsMaven $jdk @('dependency:build-classpath','-Dmdep.includeScope=test','-Dmdep.outputFile=target/gmy-r3-classpath.txt') | Out-Host
    $classpath=(Join-Path $script:CmsRoot 'target/test-classes')+';'+(Join-Path $script:CmsRoot 'target/classes')+';'+(Get-Content 'target/gmy-r3-classpath.txt' -Raw).Trim()
    $run=Join-Path $script:CmsRoot ('.local/gmy-r3-'+[Guid]::NewGuid().ToString('N').Substring(0,12))
    New-Item -ItemType Directory -Path $run | Out-Null
    $result=[ordered]@{ task='GMY R4'; engineeringPassed=$false; releaseReady=$false; pendingMembers=@('GJT R4','SR R4','YC R4','YYX R4'); passed=$false; skipBuild=[bool]$SkipBuild; baseCommit=(& git rev-parse HEAD); workingTreeDirty=[bool](& git status --porcelain); tomcatVersion=$serverVersion; container='external Apache Tomcat distribution / Bootstrap'; javaHome=$jdk; runDirectory=$run; baseUrl="http://127.0.0.1:$Port/cms"; startedAt=[DateTime]::UtcNow.ToString('o') }
    $result.codeFingerprint=(& (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR4Delivery fingerprint).Trim()
    if ($LASTEXITCODE -ne 0) { throw '验收源代码指纹生成失败。' }
    $unit=@{tests=0;failures=0;errors=0;skipped=0}
    foreach($file in Get-ChildItem -LiteralPath 'target/surefire-reports' -Filter 'TEST-*.xml') {
        [xml]$xml=Get-Content -LiteralPath $file.FullName -Raw
        foreach($key in @('tests','failures','errors','skipped')) { $unit[$key]+=[int]$xml.testsuite.GetAttribute($key) }
    }
    $result.junit=$unit
    $result.warDeployment=Get-Content -LiteralPath 'target/failsafe-reports/failsafe-summary.xml' -Raw
    $result.sharedUiPassed=$true; $result.foundationNegativePassed=$true
    $process=$null; $prepared=$false; $failure=$null; $upgradePrepared=$false; $upgradeRun=$null
    try {
        & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR3Fixture prepare $run
        if ($LASTEXITCODE -ne 0) { throw '专用 MySQL 测试库准备失败。' }; $prepared=$true
        $base=Join-Path $run 'tomcat'
        New-Item -ItemType Directory -Path $base,(Join-Path $base 'conf'),(Join-Path $base 'webapps'),(Join-Path $base 'logs'),(Join-Path $base 'temp'),(Join-Path $base 'work') -Force | Out-Null
        Copy-Item -Path (Join-Path $TomcatHome 'conf/*') -Destination (Join-Path $base 'conf') -Recurse
        @"
<?xml version="1.0" encoding="UTF-8"?>
<Server port="-1"><Service name="Catalina">
<Connector address="127.0.0.1" port="$Port" protocol="HTTP/1.1" connectionTimeout="20000" URIEncoding="UTF-8" />
<Engine name="Catalina" defaultHost="localhost"><Host name="localhost" appBase="webapps" unpackWARs="true" autoDeploy="false">
<Valve className="org.apache.catalina.valves.ErrorReportValve" showReport="false" showServerInfo="false" />
</Host></Engine></Service></Server>
"@ | Set-Content -LiteralPath (Join-Path $base 'conf/server.xml') -Encoding UTF8
        $war=Join-Path $script:CmsRoot 'target/cms.war'
        Copy-Item -LiteralPath $war -Destination (Join-Path $base 'webapps/cms.war')
        $result.warSha256=(Get-FileHash -LiteralPath $war -Algorithm SHA256).Hash.ToLowerInvariant()
        $argsList=@('--add-opens=java.base/java.lang=ALL-UNNAMED','-Dfile.encoding=UTF-8',"-Dcatalina.home=$TomcatHome","-Dcatalina.base=$base", "-Djava.io.tmpdir=$base/temp", "-Djava.util.logging.config.file=$base/conf/logging.properties",'-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',"-Dcms.config.file=$run/fixture-jdbc.properties",'-cp',"$TomcatHome/bin/bootstrap.jar;$TomcatHome/bin/tomcat-juli.jar",'org.apache.catalina.startup.Bootstrap','start')
        if ($argsList | Where-Object { $_.Contains('"') }) { throw '启动路径不能包含双引号。' }
        $quoted=$argsList | ForEach-Object { '"'+$_+'"' }
        $process=Start-Process -FilePath (Join-Path $jdk 'bin/java.exe') -ArgumentList $quoted -WorkingDirectory $run -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'tomcat-stdout.log') -RedirectStandardError (Join-Path $run 'tomcat-stderr.log')
        $result.processId=$process.Id
        $clock=[Diagnostics.Stopwatch]::StartNew(); $ready=$false
        while ($clock.Elapsed.TotalSeconds -lt 90) {
            $process.Refresh(); if ($process.HasExited) { throw '外部 Tomcat 提前退出，请查看验收目录日志。' }
            try { $probe=Invoke-WebRequest -UseBasicParsing -Uri "$($result.baseUrl)/api/system/health" -TimeoutSec 2; $ready=$probe.StatusCode -eq 200 } catch { }
            if ($ready) { break }; Start-Sleep -Milliseconds 500
        }
        if (-not $ready) { throw '外部 Tomcat 启动超时，请查看验收目录日志。' }
        $result.startupMs=$clock.ElapsedMilliseconds
        & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR3HttpSuite $result.baseUrl $run
        if ($LASTEXITCODE -ne 0) { throw '跨模块 HTTP 验收失败，请查看 http-results.json。' }
        $result.http=Get-Content -LiteralPath (Join-Path $run 'http-results.json') -Raw | ConvertFrom-Json
        & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR4HttpSuite $result.baseUrl $run
        if ($LASTEXITCODE -ne 0) { throw '第四轮真实交接与回滚验收失败，请查看 r4-http-results.json。' }
        $result.r4Http=Get-Content -LiteralPath (Join-Path $run 'r4-http-results.json') -Raw | ConvertFrom-Json
        $result.freshMigration=Get-Content -LiteralPath (Join-Path $run 'migration-results.json') -Raw | ConvertFrom-Json
        $upgradeRun=Join-Path $script:CmsRoot ('.local/gmy-r3-'+[Guid]::NewGuid().ToString('N').Substring(0,12))
        & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR3Fixture prepare $upgradeRun
        if ($LASTEXITCODE -ne 0) { throw '旧库升级专用环境准备失败。' }; $upgradePrepared=$true
        & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR4UpgradeProbe $upgradeRun
        if ($LASTEXITCODE -ne 0) { throw '九表旧库增量升级验收失败。' }
        $result.legacyUpgrade=Get-Content -LiteralPath (Join-Path $upgradeRun 'r4-upgrade-results.json') -Raw | ConvertFrom-Json
        $result.passed=$true
    } catch {
        $failure=$_; $result.failure=$_.Exception.Message
    } finally {
        if ($process) {
            $process.Refresh()
            if (-not $process.HasExited) { Stop-Process -Id $process.Id; $process.WaitForExit(10000) | Out-Null }
            $result.serverStopped=$true
        }
        if ($prepared) {
            & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR3Fixture cleanup $run
            if ($LASTEXITCODE -eq 0) { $result.cleanup=Get-Content -LiteralPath (Join-Path $run 'cleanup.json') -Raw | ConvertFrom-Json }
            else { $result.passed=$false; $result.cleanupFailed=$true }
        }
        if ($upgradePrepared) {
            & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR3Fixture cleanup $upgradeRun
            if ($LASTEXITCODE -eq 0) { $result.upgradeCleanup=Get-Content -LiteralPath (Join-Path $upgradeRun 'cleanup.json') -Raw | ConvertFrom-Json }
            else { $result.passed=$false; $result.upgradeCleanupFailed=$true }
        }
        $endFingerprint=(& (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR4Delivery fingerprint).Trim()
        if ($LASTEXITCODE -ne 0 -or $endFingerprint -ne $result.codeFingerprint) { $result.passed=$false; $result.sourceChangedDuringTest=$true }
        $result.engineeringPassed=$result.passed
        $result.completedAt=[DateTime]::UtcNow.ToString('o')
        $result | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $run 'r4-verification.json') -Encoding UTF8
        Write-Output "第四轮工程验收报告：$run/r4-verification.json；全组最终发布仍待成员第四轮验收。"
    }
    if ($failure) { throw $failure }
    if (-not $result.passed) { throw '验收未完成或清理失败，请查看报告。' }
} finally { Pop-Location }
