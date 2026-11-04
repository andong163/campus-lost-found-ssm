param([string]$Version = '9.0.123')
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^9\.0\.\d+$') { throw '只能安装传统 SSM 使用的 Tomcat 9.0.x。' }
$repository = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $repository '.tools'
$destination = Join-Path $tools "apache-tomcat-$Version"
if (Test-Path -LiteralPath (Join-Path $destination 'lib/catalina.jar')) { Write-Output $destination; return }
New-Item -ItemType Directory -Path $tools -Force | Out-Null
$name = "apache-tomcat-$Version.zip"
$archive = Join-Path $tools $name
$download = "https://dlcdn.apache.org/tomcat/tomcat-9/v$Version/bin/$name"
$checksum = "https://downloads.apache.org/tomcat/tomcat-9/v$Version/bin/$name.sha512"
Invoke-WebRequest -UseBasicParsing -Uri $download -OutFile $archive
$expected = ((Invoke-WebRequest -UseBasicParsing -Uri $checksum).Content -split '\s+')[0].Trim()
if ($expected -notmatch '^[0-9a-fA-F]{128}$' -or (Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ine $expected) {
    throw 'Apache 发布包 SHA-512 校验失败，不解压或运行。'
}
if (Test-Path -LiteralPath $destination) { throw '目标目录已存在且不完整，请人工核对，工具不会覆盖。' }
Expand-Archive -LiteralPath $archive -DestinationPath $tools
Write-Output $destination
