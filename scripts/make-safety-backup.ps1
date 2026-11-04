# WWTRADING - Phase 2 Independent Safety Backup Script
# Creates a standalone, verified safety backup of all 10 live PostgreSQL databases
# and all 15 named Docker volumes before any migration.
# Read-only volume mounts. No service downtime. Zero destructive actions.

$ErrorActionPreference = "Stop"

$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $PSScriptRoot "..\backups\safety-backup-$Timestamp"

if (Test-Path $BackupDir) {
    throw "Safety backup directory already exists: $BackupDir. Aborting to avoid overwrite."
}

New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
$AbsBackupDir = (Resolve-Path $BackupDir).Path
$DockerBackupPath = $AbsBackupDir -replace '\\', '/'

Write-Host "=========================================================="
Write-Host "WWTRADING PHASE 2 - INDEPENDENT SAFETY BACKUP"
Write-Host "Target Directory: $AbsBackupDir"
Write-Host "Timestamp:        $Timestamp"
Write-Host "=========================================================="

# Safety check on original backup:
$OriginalBackup = Join-Path $PSScriptRoot "..\backups\full-backup-20261103-1020"
if (-not (Test-Path $OriginalBackup)) {
    throw "CRITICAL: Original backup folder not found at $OriginalBackup!"
}
Write-Host "Confirmed original backup exists and is preserved at: $OriginalBackup" -ForegroundColor Green

# -----------------------------------------------------------------------------
# 1. Back up 10 Live PostgreSQL Databases (pg_dump -Fc)
# -----------------------------------------------------------------------------
$Databases = @(
    @{ Container = "wwtrading-keycloak-db-1";     Db = "keycloak";                User = "keycloak";                OutFile = "wwtrading-keycloak-db-1.dump" },
    @{ Container = "wwtrading-user-db-1";         Db = "wwtrading_users";         User = "wwtrading_users";         OutFile = "wwtrading-user-db-1.dump" },
    @{ Container = "wwtrading-order-db-1";        Db = "wwtrading_orders";        User = "wwtrading_orders";        OutFile = "wwtrading-order-db-1.dump" },
    @{ Container = "wwtrading-product-db-1";      Db = "wwtrading_products";      User = "wwtrading_products";      OutFile = "wwtrading-product-db-1.dump" },
    @{ Container = "wwtrading-quotation-db-1";    Db = "wwtrading_quotations";    User = "wwtrading_quotations";    OutFile = "wwtrading-quotation-db-1.dump" },
    @{ Container = "wwtrading-maintenance-db-1";  Db = "wwtrading_maintenance";   User = "wwtrading_maintenance";   OutFile = "wwtrading-maintenance-db-1.dump" },
    @{ Container = "wwtrading-notification-db-1"; Db = "wwtrading_notifications"; User = "wwtrading_notifications"; OutFile = "wwtrading-notification-db-1.dump" },
    @{ Container = "wwtrading-cart-db-1";         Db = "wwtrading_carts";         User = "wwtrading_carts";         OutFile = "wwtrading-cart-db-1.dump" },
    @{ Container = "wwtrading-wishlist-db-1";     Db = "wwtrading_wishlists";     User = "wwtrading_wishlists";     OutFile = "wwtrading-wishlist-db-1.dump" },
    @{ Container = "wwtrading-support-db-1";      Db = "wwtrading_support";       User = "wwtrading_support";       OutFile = "wwtrading-support-db-1.dump" }
)

Write-Host "`n--- STEP 1: DUMPING 10 LIVE POSTGRESQL DATABASES ---"
$DbResults = @()

foreach ($dbSpec in $Databases) {
    $c = $dbSpec.Container
    $d = $dbSpec.Db
    $u = $dbSpec.User
    $out = $dbSpec.OutFile
    $localTarget = Join-Path $AbsBackupDir $out
    $tmpFile = "/tmp/$out"

    Write-Host -NoNewline "Dumping $d from $c ($u)... "

    $dumpCmd = "pg_dump -U $u -d $d -Fc -f $tmpFile"
    $execRes = (& docker exec $c sh -c "$dumpCmd" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED!" -ForegroundColor Red
        throw ("pg_dump failed on " + $c + " for " + $d + ": " + $execRes)
    }

    & docker cp "${c}:${tmpFile}" "$localTarget"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "COPY FAILED!" -ForegroundColor Red
        throw ("docker cp failed for " + $tmpFile + " from " + $c)
    }

    & docker exec $c rm -f $tmpFile

    $fileInfo = Get-Item $localTarget
    Write-Host "OK ($($fileInfo.Length) bytes)" -ForegroundColor Green

    $DbResults += [PSCustomObject]@{
        Name = $d
        Container = $c
        User = $u
        File = $out
        SizeBytes = $fileInfo.Length
    }
}

# -----------------------------------------------------------------------------
# 2. Back up 15 Named Docker Volumes (tar.gz with :ro mounts)
# -----------------------------------------------------------------------------
$Volumes = @(
    @{ Volume = "wwtrading_cart-data";                    Archive = "volume-wwtrading_cart-data.tar.gz";                    Type = "PostgreSQL DB Data" },
    @{ Volume = "wwtrading_kafka-data";                   Archive = "volume-wwtrading_kafka-data.tar.gz";                   Type = "Kafka Broker Message Logs" },
    @{ Volume = "wwtrading_keycloak-data";                Archive = "volume-wwtrading_keycloak-data.tar.gz";                Type = "Keycloak PostgreSQL DB Data (Safety Copy)" },
    @{ Volume = "wwtrading_maintenance-attachments-data"; Archive = "volume-wwtrading_maintenance-attachments-data.tar.gz"; Type = "Maintenance Attachments" },
    @{ Volume = "wwtrading_maintenance-data";             Archive = "volume-wwtrading_maintenance-data.tar.gz";             Type = "PostgreSQL DB Data" },
    @{ Volume = "wwtrading_notification-data";            Archive = "volume-wwtrading_notification-data.tar.gz";            Type = "PostgreSQL DB Data" },
    @{ Volume = "wwtrading_order-data";                   Archive = "volume-wwtrading_order-data.tar.gz";                   Type = "PostgreSQL DB Data" },
    @{ Volume = "wwtrading_order-transfer-proofs-data";   Archive = "volume-wwtrading_order-transfer-proofs-data.tar.gz";   Type = "Customer Order Bank Transfer Proofs" },
    @{ Volume = "wwtrading_product-data";                 Archive = "volume-wwtrading_product-data.tar.gz";                 Type = "PostgreSQL DB Data" },
    @{ Volume = "wwtrading_product-images-data";          Archive = "volume-wwtrading_product-images-data.tar.gz";          Type = "Catalog Product Images" },
    @{ Volume = "wwtrading_quotation-data";               Archive = "volume-wwtrading_quotation-data.tar.gz";               Type = "PostgreSQL DB Data" },
    @{ Volume = "wwtrading_support-attachments-data";     Archive = "volume-wwtrading_support-attachments-data.tar.gz";     Type = "Support Chat Attachments" },
    @{ Volume = "wwtrading_support-data";                 Archive = "volume-wwtrading_support-data.tar.gz";                 Type = "PostgreSQL DB Data" },
    @{ Volume = "wwtrading_user-data";                    Archive = "volume-wwtrading_user-data.tar.gz";                    Type = "PostgreSQL DB Data" },
    @{ Volume = "wwtrading_wishlist-data";                Archive = "volume-wwtrading_wishlist-data.tar.gz";                Type = "PostgreSQL DB Data" }
)

Write-Host "`n--- STEP 2: ARCHIVING 15 NAMED DOCKER VOLUMES (READ-ONLY MOUNTS) ---"
$VolResults = @()

foreach ($volSpec in $Volumes) {
    $v = $volSpec.Volume
    $arch = $volSpec.Archive
    $type = $volSpec.Type
    $localTarget = Join-Path $AbsBackupDir $arch

    Write-Host -NoNewline "Archiving volume $v ($type)... "

    # Archive volume using temporary alpine container mounting volume :ro
    $tarCmd = "docker run --rm -v ${v}:/v_data:ro -v `"${DockerBackupPath}:/backup`" alpine tar -czf /backup/$arch -C /v_data ."
    $tarRes = Invoke-Expression $tarCmd 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED!" -ForegroundColor Red
        throw ("Failed to archive volume " + $v + ": " + $tarRes)
    }

    $fileInfo = Get-Item $localTarget
    Write-Host "OK ($($fileInfo.Length) bytes)" -ForegroundColor Green

    $VolResults += [PSCustomObject]@{
        Volume = $v
        Archive = $arch
        Type = $type
        SizeBytes = $fileInfo.Length
    }
}

# -----------------------------------------------------------------------------
# 3. Validation & Integrity Verification
# -----------------------------------------------------------------------------
Write-Host "`n--- STEP 3: VALIDATION AND INTEGRITY AUDIT ---"

# 3.1 Validate PostgreSQL Dumps with pg_restore --list
$DbValidation = @()
foreach ($dbSpec in $Databases) {
    $c = $dbSpec.Container
    $d = $dbSpec.Db
    $out = $dbSpec.OutFile
    $localTarget = Join-Path $AbsBackupDir $out

    Write-Host -NoNewline "Validating pg_restore --list: $out ... "

    $tmpVerify = "/tmp/verify_$out"
    & docker cp "$localTarget" "${c}:${tmpVerify}"
    $restoreList = & docker exec $c pg_restore --list $tmpVerify 2>&1
    $restoreListStr = ($restoreList | Out-String)
    $exitCode = $LASTEXITCODE
    & docker exec $c rm -f $tmpVerify

    if ($exitCode -eq 0 -and $restoreListStr -match "Format: CUSTOM" -and $restoreListStr -match "TOC Entries:") {
        $tocMatches = [regex]::Match($restoreListStr, "TOC Entries:\s+(\d+)")
        $tocCount = if ($tocMatches.Success) { [int]$tocMatches.Groups[1].Value } else { 0 }
        Write-Host "VALID (Format: CUSTOM, TOC Entries: $tocCount)" -ForegroundColor Green
        $DbValidation += [PSCustomObject]@{
            File = $out
            Database = $d
            Status = "VALID"
            TocEntries = $tocCount
        }
    } else {
        Write-Host "CORRUPT OR INVALID!" -ForegroundColor Red
        throw ("pg_restore verification failed for " + $out + ": " + $restoreListStr)
    }
}

# 3.2 Validate Volume Archives with tar -tzf
$VolValidation = @()
foreach ($volSpec in $Volumes) {
    $arch = $volSpec.Archive
    $v = $volSpec.Volume
    Write-Host -NoNewline "Validating tar -tzf: $arch ... "

    $testCmd = "docker run --rm -v `"${DockerBackupPath}:/backup:ro`" alpine tar -tzf /backup/$arch"
    $fileList = Invoke-Expression $testCmd 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -eq 0) {
        $entryCount = ($fileList | Measure-Object).Count
        Write-Host "VALID ($entryCount entries)" -ForegroundColor Green
        $VolValidation += [PSCustomObject]@{
            Archive = $arch
            Volume = $v
            Status = "VALID"
            EntryCount = $entryCount
        }
    } else {
        Write-Host "FAILED!" -ForegroundColor Red
        throw ("Volume archive verification failed for " + $arch + ": " + $fileList)
    }
}

# -----------------------------------------------------------------------------
# 4. Generate Checksums and Manifests
# -----------------------------------------------------------------------------
Write-Host "`n--- STEP 4: GENERATING MANIFEST & SHA-256 CHECKSUMS ---"
$ManifestList = @()
$AllBackupFiles = Get-ChildItem -Path $AbsBackupDir -File

$TotalSizeBytes = 0
foreach ($f in $AllBackupFiles) {
    $TotalSizeBytes += $f.Length
    $hash = (Get-FileHash -Path $f.FullName -Algorithm SHA256).Hash
    Write-Host "$($f.Name) | $($f.Length) bytes | $hash"
    $ManifestList += [PSCustomObject]@{
        Filename = $f.Name
        SizeBytes = $f.Length
        SHA256 = $hash
        Created = $f.CreationTimeUtc.ToString("o")
    }
}

$ManifestList | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $AbsBackupDir "MANIFEST.json") -Encoding UTF8

$TotalSizeMB = [math]::Round($TotalSizeBytes / 1MB, 2)

$ManifestText = @"
================================================================================
WWTRADING INDEPENDENT SAFETY BACKUP MANIFEST (PHASE 2)
Timestamp: $Timestamp
Directory: $AbsBackupDir
Created at (UTC): $([DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss"))
Total Files: $($ManifestList.Count) (10 Database Dumps + 15 Volume Archives)
Total Size:  $TotalSizeBytes bytes (~$TotalSizeMB MB)
================================================================================

POSTGRESQL 17 CUSTOM-FORMAT DUMPS (10):
$($DbResults | Format-Table -AutoSize | Out-String)

NAMED DOCKER VOLUMES (.tar.gz) (15):
$($VolResults | Format-Table -AutoSize | Out-String)

SHA-256 CHECKSUMS:
$($ManifestList | Format-Table -AutoSize | Out-String)
"@

Set-Content -Path (Join-Path $AbsBackupDir "MANIFEST.txt") -Value $ManifestText -Encoding UTF8

Write-Host "`n=========================================================="
Write-Host "PHASE 2 SAFETY BACKUP COMPLETE AND VERIFIED"
Write-Host "Total Files: $($ManifestList.Count)"
Write-Host "Total Size:  ~$TotalSizeMB MB ($TotalSizeBytes bytes)"
Write-Host "Manifest:    $AbsBackupDir\MANIFEST.json"
Write-Host "=========================================================="

