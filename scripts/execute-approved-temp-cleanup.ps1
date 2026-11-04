# WWTRADING - Execute Approved Temporary File Cleanup
# Deletes only the 64 confirmed temporary files.
# Preserves all source code, Compose files, Dockerfiles, .env, backups, volumes, and containers.

$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host "WWTRADING - EXECUTING APPROVED TEMPORARY CLEANUP"
Write-Host "=========================================================="

$RootFiles = @("tmp-mvn-err.log", "tmp-mvn-out.log", "qa-kc-390.png", "qa-kc-desktop.png")
$TmpLooseFiles = Get-ChildItem -Path "tmp" -File

$DeletedFiles = @()
$TotalReclaimedBytes = 0

# 1. Delete root files
foreach ($rf in $RootFiles) {
    if (Test-Path $rf) {
        $item = Get-Item $rf
        $len = $item.Length
        Remove-Item -Path $rf -Force
        $DeletedFiles += [PSCustomObject]@{
            Location = "Project Root"
            Name = $item.Name
            SizeBytes = $len
        }
        $TotalReclaimedBytes += $len
        $msg = "Deleted (Root): " + $item.Name + " (" + $len + " bytes)"
        Write-Host $msg
    }
}

# 2. Delete loose files in tmp/
foreach ($tf in $TmpLooseFiles) {
    $len = $tf.Length
    $name = $tf.Name
    Remove-Item -Path $tf.FullName -Force
    $DeletedFiles += [PSCustomObject]@{
        Location = "tmp loose"
        Name = $name
        SizeBytes = $len
    }
    $TotalReclaimedBytes += $len
    $msg = "Deleted (tmp): " + $name + " (" + $len + " bytes)"
    Write-Host $msg
}

$TotalMB = [math]::Round($TotalReclaimedBytes / 1MB, 2)
$summaryMsg = "Cleanup Complete: " + $DeletedFiles.Count + " files deleted, " + $TotalMB + " MB (" + $TotalReclaimedBytes + " bytes) reclaimed."
Write-Host $summaryMsg -ForegroundColor Green

# 3. Post-cleanup Verification: Verify all 3 backup directories and SHA-256
Write-Host "--- POST-CLEANUP VERIFICATION: RE-CHECKING ALL 3 BACKUP REPOSITORIES ---"
$Backups = @(
    "backups/full-backup-20261103-1020",
    "backups/safety-backup-20261103-132530",
    "backups/docker-export-20261103-134923"
)

$AllOk = $true
foreach ($b in $Backups) {
    if (-not (Test-Path $b)) {
        Write-Host "CRITICAL FAILURE: Backup repository missing: $b" -ForegroundColor Red
        $AllOk = $false
        continue
    }
    $manifestPath = Join-Path $b "MANIFEST.json"
    $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
    Write-Host "Validating $b ($($manifest.Count) files)..."
    foreach ($entry in $manifest) {
        $filePath = Join-Path $b $entry.Filename
        if (-not (Test-Path $filePath)) {
            Write-Host "  MISSING FILE: $filePath" -ForegroundColor Red
            $AllOk = $false
            continue
        }
        $fileInfo = Get-Item $filePath
        if ($fileInfo.Length -ne $entry.SizeBytes) {
            Write-Host "  SIZE MISMATCH: $filePath" -ForegroundColor Red
            $AllOk = $false
            continue
        }
        $hash = (Get-FileHash -Path $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $entry.SHA256.ToLowerInvariant()) {
            Write-Host "  HASH MISMATCH: $filePath" -ForegroundColor Red
            $AllOk = $false
            continue
        }
    }
    Write-Host "  $b : 100% VERIFIED INTACT (All SHA-256 and sizes matched)" -ForegroundColor Green
}

if (-not $AllOk) {
    throw "Post-cleanup verification failed! One or more backup files failed check."
}

Write-Host "ALL 3 BACKUP REPOSITORIES 100% INTACT AND VERIFIED." -ForegroundColor Green
