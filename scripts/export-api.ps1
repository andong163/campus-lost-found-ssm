param([string]$JavaHome)
. "$PSScriptRoot/common.ps1"
$jdk=Resolve-CmsJava $JavaHome
Invoke-CmsMaven $jdk @('-DskipTests','test-compile','dependency:build-classpath','-Dmdep.includeScope=test','-Dmdep.outputFile=target/gmy-r3-classpath.txt') | Out-Host
$classpath=(Join-Path $script:CmsRoot 'target/test-classes')+';'+(Join-Path $script:CmsRoot 'target/classes')+';'+(Get-Content (Join-Path $script:CmsRoot 'target/gmy-r3-classpath.txt') -Raw).Trim()
Push-Location $script:CmsRoot
try {
    & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR3ApiCatalog docs/openapi.json
    if ($LASTEXITCODE -ne 0) { throw '主接口文件导出失败。' }
    # JSON is also valid YAML 1.2; preserve the established import filename as the exact same document.
    Copy-Item -LiteralPath docs/openapi.json -Destination docs/openapi.yaml
} finally { Pop-Location }
