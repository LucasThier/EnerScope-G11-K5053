param(
    [Parameter(Mandatory=$true)][Guid]$ProjectId,
    [Parameter(Mandatory=$true)][string]$AccessToken,
    [string]$ApiBase = 'http://localhost:8080/api/v1',
    [ValidateRange(1,100)][int]$Rounds = 10,
    [string]$Seed = '42',
    [switch]$Synchronous
)
$ErrorActionPreference = 'Stop'
$headers = @{ Authorization = "Bearer $AccessToken" }
$fixtures = Join-Path $PSScriptRoot '../../backend/src/test/resources/economic-examples'
$version = (Invoke-RestMethod -Method Post -Uri "$ApiBase/projects/$ProjectId/version" -Headers $headers -ContentType 'application/json' -Body '{"name":"Reproducible economic example"}').data
$config = Get-Content -Raw -LiteralPath (Join-Path $fixtures 'economic-configuration.json')
$sourceIds = @('11111111-1111-1111-1111-111111111111','22222222-2222-2222-2222-222222222222')
for ($index = 0; $index -lt 2; $index++) {
    $node = Get-Content -Raw -LiteralPath (Join-Path $fixtures 'economic-well.json') | ConvertFrom-Json
    $node.startupDate = @('2031-01-01T00:00:00Z','2032-01-01T00:00:00Z')[$index]
    $node.maxCollectionCapacity = @(100,500)[$index]
    $created = (Invoke-RestMethod -Method Post -Uri "$ApiBase/version/$($version.id)/node" -Headers $headers -ContentType 'application/json' -Body ($node | ConvertTo-Json -Depth 50)).data
    $config = $config.Replace($sourceIds[$index], [string]$created.id)
}
$economic = "$ApiBase/projects/$ProjectId/versions/$($version.id)/economics"
$null = Invoke-RestMethod -Method Put -Uri "$economic/configuration" -Headers $headers -ContentType 'application/json' -Body $config
if ($Synchronous) {
    Invoke-RestMethod -Method Post -Uri "$economic/evaluations" -Headers $headers
    return
}
$job = (Invoke-RestMethod -Method Post -Uri "$economic/evaluation-jobs" -Headers $headers -ContentType 'application/json' -Body (@{rounds=$Rounds;seed=$Seed} | ConvertTo-Json)).data
while ($job.status -in @('QUEUED','RUNNING')) {
    Start-Sleep -Seconds 2
    $job = (Invoke-RestMethod -Uri "$economic/evaluation-jobs/$($job.id)" -Headers $headers).data
}
if ($job.status -ne 'COMPLETED') { throw "Job $($job.id): $($job.status). $($job.errorMessage)" }
Invoke-RestMethod -Uri "$economic/evaluations/$($job.evaluationId)" -Headers $headers
Invoke-RestMethod -Uri "$economic/evaluations/$($job.evaluationId)/rounds" -Headers $headers
