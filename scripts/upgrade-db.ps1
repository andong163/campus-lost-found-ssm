param([string]$JavaHome)
. "$PSScriptRoot/common.ps1"
$jdk = Resolve-CmsJava $JavaHome
Invoke-CmsMaven $jdk @('-DskipTests', 'test-compile')
$classpath = Get-CmsToolClasspath $jdk
Push-Location $script:CmsRoot
try {
    & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' '-Dlogback.configurationFile=src/main/resources/logback.xml' -cp $classpath com.team.cms.tools.MySqlCampusUpgrade
    if ($LASTEXITCODE -ne 0) { throw '数据库升级未完成，请按 docs/database-upgrade.md 检查。' }
} finally { Pop-Location }
