param([string]$JavaHome)
. "$PSScriptRoot/common.ps1"
& "$PSScriptRoot/verify-foundation.ps1" -SourceOnly
Invoke-CmsMaven $JavaHome @('clean', 'verify')
& "$PSScriptRoot/verify-foundation.ps1"
