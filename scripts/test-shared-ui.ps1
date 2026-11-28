$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$node = (Get-Command node.exe -ErrorAction Stop).Source
$testFiles = Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'src/test/js') -Filter '*.test.cjs' |
    Sort-Object Name | Select-Object -ExpandProperty FullName
& $node --test @testFiles
if ($LASTEXITCODE -ne 0) { throw '前端 JavaScript 检查失败。' }
