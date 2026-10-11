param([string]$JavaHome, [ValidateRange(1024,65535)][int]$Port = 8080)
. "$PSScriptRoot/common.ps1"
$jdk = Resolve-CmsJava $JavaHome
Invoke-CmsMaven $jdk @('-DskipTests', 'package')
$classpath = Get-CmsToolClasspath $jdk -ContainerOnly
Push-Location $script:CmsRoot
try {
    & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' "-Dcms.port=$Port" -cp $classpath com.team.cms.tools.LocalServer
    if ($LASTEXITCODE -ne 0) { throw '本地服务器启动失败。' }
} finally { Pop-Location }
