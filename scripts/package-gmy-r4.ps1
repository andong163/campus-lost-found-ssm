param([Parameter(Mandatory=$true)][string]$VerificationPath, [string]$JavaHome, [string]$OutputDirectory)
. "$PSScriptRoot/common.ps1"
$jdk=Resolve-CmsJava $JavaHome
Push-Location $script:CmsRoot
try {
    if (-not (Test-Path -LiteralPath 'target/gmy-r3-classpath.txt')) { throw '先运行 ./scripts/test-gmy-r4.ps1 完成当前源码验收。' }
    & "$PSScriptRoot/verify-foundation.ps1"
    $classpath=(Join-Path $script:CmsRoot 'target/test-classes')+';'+(Join-Path $script:CmsRoot 'target/classes')+';'+(Get-Content -LiteralPath 'target/gmy-r3-classpath.txt' -Raw).Trim()
    $verification=(Resolve-Path -LiteralPath $VerificationPath).Path
    if (-not $OutputDirectory) { $OutputDirectory=Join-Path $script:CmsRoot ('dist/gmy-r4-candidate-'+(Get-Date -Format 'yyyyMMdd-HHmmss')) }
    & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $classpath com.team.cms.tools.GmyR4Delivery package $verification $OutputDirectory
    if ($LASTEXITCODE -ne 0) { throw '候选交付包生成失败，请检查验收记录和源码指纹。' }
} finally { Pop-Location }
