param(
    [switch]$InstallFrontend,
    [switch]$SkipDockerConfig
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    Write-Host ""
    Write-Host "==> $Name" -ForegroundColor Cyan
    & $Command
}

Invoke-Step "Backend tests" {
    Push-Location "$repoRoot\backend"
    try {
        mvn.cmd test
    } finally {
        Pop-Location
    }
}

Invoke-Step "Service reactor tests" {
    Push-Location "$repoRoot\services"
    try {
        mvn.cmd test
    } finally {
        Pop-Location
    }
}

Invoke-Step "Frontend build" {
    Push-Location "$repoRoot\frontend"
    try {
        if ($InstallFrontend) {
            npm.cmd ci
        }
        npm.cmd run build
    } finally {
        Pop-Location
    }
}

if (-not $SkipDockerConfig) {
    Invoke-Step "Docker Compose config" {
        $docker = Get-Command docker -ErrorAction SilentlyContinue
        if (-not $docker) {
            Write-Host "SKIP: docker is not installed on this machine." -ForegroundColor Yellow
            return
        }

        $previousDbPassword = $env:LIFELINE_DB_PASSWORD
        $previousJwtSecret = $env:LIFELINE_JWT_SECRET
        $previousDemoPassword = $env:LIFELINE_DEMO_PASSWORD
        try {
            $env:LIFELINE_DB_PASSWORD = [guid]::NewGuid().ToString()
            $env:LIFELINE_JWT_SECRET = "$([guid]::NewGuid())$([guid]::NewGuid())"
            $env:LIFELINE_DEMO_PASSWORD = [guid]::NewGuid().ToString()
            Push-Location $repoRoot
            docker compose config
        } finally {
            Pop-Location
            $env:LIFELINE_DB_PASSWORD = $previousDbPassword
            $env:LIFELINE_JWT_SECRET = $previousJwtSecret
            $env:LIFELINE_DEMO_PASSWORD = $previousDemoPassword
        }
    }
}

Write-Host ""
Write-Host "V7 verification completed." -ForegroundColor Green
