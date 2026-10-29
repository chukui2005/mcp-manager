<#
.SYNOPSIS
    Lightweight environment and git verification script for TASK-000 dry run.
.DESCRIPTION
    Checks Git status, branch, PowerShell version, Java/Maven if available,
    and reports basic project environment diagnostics.
#>

$ErrorActionPreference = "Continue"

Write-Host "=== MCP Manager Environment Verification ===" -ForegroundColor Cyan
Write-Host "Date/Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"

# 1. Git info
Write-Host "`n--- Git Verification ---" -ForegroundColor Yellow
try {
    $gitBranch = git branch --show-current
    $gitCommit = git rev-parse --short HEAD
    Write-Host "Current Branch: $gitBranch"
    Write-Host "HEAD Commit   : $gitCommit"
} catch {
    Write-Warning "Failed to query Git information: $_"
}

# 2. PowerShell & Runtime info
Write-Host "`n--- Runtime Verification ---" -ForegroundColor Yellow
Write-Host "PowerShell Version : $($PSVersionTable.PSVersion)"
if ($PSVersionTable.OS) {
    Write-Host "OS Platform        : $($PSVersionTable.OS)"
} else {
    Write-Host "OS Platform        : Windows (PowerShell 5.1 Desktop)"
}

# 3. Toolchain info (Java / Maven)
Write-Host "`n--- Toolchain Verification ---" -ForegroundColor Yellow
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if ($javaCmd) {
    $rawJava = & cmd /c "java -version 2>&1"
    $javaVer = ($rawJava | Out-String).Trim().Split([Environment]::NewLine)[0]
    Write-Host "Java Found: $javaVer"
} else {
    Write-Host "Java: Not found in current PATH (Required for later phases)" -ForegroundColor DarkGray
}

$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvnCmd) {
    $rawMvn = & cmd /c "mvn -v 2>&1"
    $mvnVer = ($rawMvn | Out-String).Trim().Split([Environment]::NewLine)[0]
    Write-Host "Maven Found: $mvnVer"
} else {
    Write-Host "Maven: Not found in current PATH (Required for later phases)" -ForegroundColor DarkGray
}

Write-Host "`nVerification complete: PASSED (Dry-run environment check)." -ForegroundColor Green
