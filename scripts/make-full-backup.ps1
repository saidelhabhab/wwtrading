# WWTRADING Full Backup Script
# Creates a full, standalone, verified backup of all 10 PostgreSQL databases and persistent volumes.
# Strictly read-only on the source data. No drops, no deletes, no overwrites.

$ErrorActionPreference = "Stop"

$Timestamp = "20261103-1020"
$BackupDir = Join-Path $PSScriptRoot "..\backups\full-backup-$Timestamp"
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$AbsBackupDir = (Resolve-Path $BackupDir).Path
Write-Host "=========================================================="
Write-Host "WWTRADING FULL BACKUP - STARTING"
Write-Host "Destination: $AbsBackupDir"
Write-Host "Timestamp:   $Timestamp"
Write-Host "=========================================================="

# 1. Database Specifications (10 Databases)
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

Write-Host "`n--- STEP 1: DUMPING 10 POSTGRESQL DATABASES (pg_dump -Fc) ---"
$DbResults = @()

foreach ($dbSpec in $Databases) {
    $c = $dbSpec.Container
    $d = $dbSpec.Db
    $u = $dbSpec.User
    $out = $dbSpec.OutFile
    $localTarget = Join-Path $AbsBackupDir $out
    $tmpFile = "/tmp/$out"

    Write-Host -NoNewline "Dumping $d from $c ($u)... "

    # Run pg_dump inside container to /tmp
    $dumpCmd = "pg_dump -U $u -d $d -Fc -f $tmpFile"
    $execRes = & docker exec $c sh -c "$dumpCmd" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED!" -ForegroundColor Red
        throw ("pg_dump failed on " + $c + " for " + $d + ": " + $execRes)
    }

    # Copy dump out to local directory
    & docker cp "${c}:${tmpFile}" "$localTarget"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "COPY FAILED!" -ForegroundColor Red
        throw ("docker cp failed for " + $tmpFile + " from " + $c)
    }

    # Clean up /tmp file inside container
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

# 2. Volume Specifications
$Volumes = @(
    @{ Volume = "wwtrading_order-transfer-proofs-data";   Archive = "volume-wwtrading_order-transfer-proofs-data.tar.gz";   Description = "Order Bank Transfer Proof Uploads" },
    @{ Volume = "wwtrading_product-images-data";          Archive = "volume-wwtrading_product-images-data.tar.gz";          Description = "Product Images and Catalog Assets" },
    @{ Volume = "wwtrading_maintenance-attachments-data"; Archive = "volume-wwtrading_maintenance-attachments-data.tar.gz"; Description = "Maintenance Intervention Files" },
    @{ Volume = "wwtrading_support-attachments-data";     Archive = "volume-wwtrading_support-attachments-data.tar.gz";     Description = "Support and Chat Attachments" },
    @{ Volume = "wwtrading_kafka-data";                   Archive = "volume-wwtrading_kafka-data.tar.gz";                   Description = "Kafka Broker Message Logs" },
    @{ Volume = "wwtrading_keycloak-data";                Archive = "volume-wwtrading_keycloak-data.tar.gz";                Description = "Keycloak PostgreSQL Raw Data Volume" }
)

Write-Host "`n--- STEP 2: ARCHIVING PERSISTENT DOCKER VOLUMES (.tar.gz) ---"
$VolResults = @()

# Docker run volume mount path on Windows format
$DockerHostPath = $AbsBackupDir -replace '\\', '/'

foreach ($volSpec in $Volumes) {
    $v = $volSpec.Volume
    $arch = $volSpec.Archive
    $desc = $volSpec.Description
    $localTarget = Join-Path $AbsBackupDir $arch

    Write-Host -NoNewline "Archiving volume $v ($desc)... "

    # Archive volume data using temporary alpine container mounted read-only
    $tarCmd = "docker run --rm -v ${v}:/v_data:ro -v `"${DockerHostPath}:/backup`" alpine tar -czf /backup/$arch -C /v_data ."
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
        Description = $desc
        SizeBytes = $fileInfo.Length
    }
}

Write-Host "`n--- STEP 3: VERIFICATION AND INTEGRITY AUDIT ---"

# Verify PostgreSQL dumps with pg_restore --list
$VerificationResults = @()

foreach ($dbSpec in $Databases) {
    $c = $dbSpec.Container
    $d = $dbSpec.Db
    $out = $dbSpec.OutFile
    $localTarget = Join-Path $AbsBackupDir $out

    Write-Host -NoNewline "Verifying dump integrity: $out ... "

    # Copy dump temporarily into container to run pg_restore --list
    $tmpVerify = "/tmp/verify_$out"
    & docker cp "$localTarget" "${c}:${tmpVerify}"
    $restoreList = & docker exec $c pg_restore --list $tmpVerify 2>&1
    $restoreListStr = ($restoreList | Out-String)
    $exitCode = $LASTEXITCODE
    & docker exec $c rm -f $tmpVerify

    if ($exitCode -eq 0 -and $restoreListStr -match "Format: CUSTOM" -and $restoreListStr -match "TOC Entries:") {
        $tocMatches = [regex]::Match($restoreListStr, "TOC Entries:\s+(\d+)")
        $tocCount = if ($tocMatches.Success) { $tocMatches.Groups[1].Value } else { "N/A" }
        Write-Host "VALID (Format: CUSTOM, TOC Entries: $tocCount)" -ForegroundColor Green
        $VerificationResults += [PSCustomObject]@{
            File = $out
            Type = "PostgreSQL Dump"
            Status = "VERIFIED_OK"
            Details = "Format: CUSTOM, TOC: $tocCount, Target DB: $d"
        }
    } else {
        Write-Host "CORRUPT OR INVALID!" -ForegroundColor Red
        throw ("Verification failed for " + $out + ": " + $restoreList)
    }
}

# Verify volume archives with tar -tzf
foreach ($volSpec in $Volumes) {
    $v = $volSpec.Volume
    $arch = $volSpec.Archive
    Write-Host -NoNewline "Verifying archive integrity: $arch ... "

    $testCmd = "docker run --rm -v `"${DockerHostPath}:/backup:ro`" alpine tar -tzf /backup/$arch"
    $fileList = Invoke-Expression $testCmd 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -eq 0) {
        $entryCount = ($fileList | Measure-Object).Count
        Write-Host "VALID ($entryCount entries in archive)" -ForegroundColor Green
        $VerificationResults += [PSCustomObject]@{
            File = $arch
            Type = "Volume Archive"
            Status = "VERIFIED_OK"
            Details = "Valid gzip tar archive, $entryCount files/dirs"
        }
    } else {
        Write-Host "FAILED!" -ForegroundColor Red
        throw ("Volume archive verification failed for " + $arch + ": " + $fileList)
    }
}

Write-Host "`n--- STEP 4: GENERATING SHA-256 MANIFEST ---"
$ManifestList = @()
$AllBackupFiles = Get-ChildItem -Path $AbsBackupDir -File

foreach ($f in $AllBackupFiles) {
    $hash = (Get-FileHash -Path $f.FullName -Algorithm SHA256).Hash
    Write-Host "$($f.Name) | $($f.Length) bytes | $hash"
    $ManifestList += [PSCustomObject]@{
        Filename = $f.Name
        SizeBytes = $f.Length
        SHA256 = $hash
        Created = $f.CreationTimeUtc.ToString("o")
    }
}

# Save manifest files
$ManifestList | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $AbsBackupDir "MANIFEST.json") -Encoding UTF8

$ManifestText = @"
================================================================================
WWTRADING COMPLETE VERIFIED BACKUP MANIFEST
Timestamp: $Timestamp
Directory: $AbsBackupDir
Created at (UTC): $([DateTime]::UtcNow.ToString("yyyy-MM-dd HH:mm:ss"))
================================================================================

POSTGRESQL 17 CUSTOM DUMPS:
$($DbResults | Format-Table -AutoSize | Out-String)

PERSISTENT VOLUMES (.tar.gz):
$($VolResults | Format-Table -AutoSize | Out-String)

SHA-256 CHECKSUMS:
$($ManifestList | Format-Table -AutoSize | Out-String)
"@

Set-Content -Path (Join-Path $AbsBackupDir "MANIFEST.txt") -Value $ManifestText -Encoding UTF8

Write-Host "`n=========================================================="
Write-Host "ALL 10 DATABASES AND 5 PERSISTENT VOLUMES BACKED UP & VERIFIED"
Write-Host "Manifest saved to $AbsBackupDir\MANIFEST.json"
Write-Host "=========================================================="
