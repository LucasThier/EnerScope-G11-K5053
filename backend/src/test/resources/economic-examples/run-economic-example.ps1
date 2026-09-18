param(
    [Parameter(Mandatory = $true)][Guid]$ProjectId,
    [Parameter(Mandatory = $true)][string]$AccessToken,
    [string]$BaseUrl = 'http://localhost:8080/api/v1'
)
$ErrorActionPreference = 'Stop'
$headers = @{ Authorization = "Bearer $AccessToken" }
function Invoke-ExampleApi([string]$Method, [string]$Path, $Body) {
    $parameters = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = ConvertTo-Json -InputObject $Body -Depth 40
    }
    (Invoke-RestMethod @parameters).data
}
# Use an existing project with edit permission (or a platform administrator).
$version = Invoke-ExampleApi 'POST' "/projects/$ProjectId/version" @{ name = 'Economic example - five years' }
$nodeIds = @()
foreach ($index in 0..1) {
    $node = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'economic-well.json') | ConvertFrom-Json
    $node.name = "Economic example well $index"
    $node.startupDate = $(if ($index -eq 0) { '2031-01-01T00:00:00Z' } else { '2032-01-01T00:00:00Z' })
    $node.maxCollectionCapacity = $(if ($index -eq 0) { 100 } else { 500 })
    $created = Invoke-ExampleApi 'POST' "/version/$($version.id)/node" $node
    $nodeIds += $created.id
}
$configurationText = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'economic-configuration.json')
$configurationText = $configurationText.Replace('11111111-1111-1111-1111-111111111111', $nodeIds[0])
$configurationText = $configurationText.Replace('22222222-2222-2222-2222-222222222222', $nodeIds[1])
$economicPath = "/projects/$ProjectId/versions/$($version.id)/economics"
$null = Invoke-ExampleApi 'PUT' "$economicPath/configuration" ($configurationText | ConvertFrom-Json)
$evaluation = Invoke-ExampleApi 'POST' "$economicPath/evaluations" $null
$evaluation.snapshot.result.periods | Format-Table period, year, taxableIncome, deductibleExpenses, taxes, cashFlow
if ([decimal]$evaluation.snapshot.result.npv -ne [decimal]169.20) { throw 'Unexpected example NPV' }
[PSCustomObject]@{ VersionId = $version.id; EvaluationId = $evaluation.id; NPV = $evaluation.snapshot.result.npv }
