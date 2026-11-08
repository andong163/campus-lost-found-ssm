$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$node = (Get-Command node.exe -ErrorAction Stop).Source
& $node --test (Join-Path $repositoryRoot 'src/test/js/shared-ui.test.cjs') (Join-Path $repositoryRoot 'src/test/js/category-select.test.cjs')
if ($LASTEXITCODE -ne 0) { throw '公共页面 JavaScript 检查失败。' }
