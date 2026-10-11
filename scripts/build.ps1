param([string]$JavaHome)
. "$PSScriptRoot/common.ps1"
Invoke-CmsMaven $JavaHome @('clean', 'verify')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $script:CmsRoot 'target/cms.war'))
try {
    $forbidden = @($archive.Entries | Where-Object { $_.FullName -match 'jdbc-local|com/team/cms/support|com/team/cms/tools|Test.class$' })
    if ($forbidden.Count -ne 0) { throw 'WAR 中含私密配置或测试类。' }
    Write-Output 'WAR checked: private config and test classes excluded.'
} finally { $archive.Dispose() }
