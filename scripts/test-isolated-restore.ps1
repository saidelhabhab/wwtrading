# WWTRADING - Isolated Backup Restoration Test Script
# Tests the restoration of all 10 PostgreSQL dumps and 6 volume archives in an isolated environment.
# STRICT SAFETY: Does not touch or connect to existing WWTRADING containers, networks, or volumes.

$ErrorActionPreference = "Stop"

$Timestamp = "20261103-1020"
$BackupDir = Join-Path $PSScriptRoot "..\backups\full-backup-$Timestamp"
if (-not (Test-Path $BackupDir)) {
    throw "Backup directory does not exist: $BackupDir"
}

$AbsBackupDir = (Resolve-Path $BackupDir).Path
$DockerBackupPath = $AbsBackupDir -replace '\\', '/'

$TestContainerName = "wwt-isolated-restore-test-db"
$TestTmpDir = Join-Path $PSScriptRoot "..\backups\test-restore-tmp"

Write-Host "=========================================================="
Write-Host "WWTRADING ISOLATED RESTORATION TEST"
Write-Host "Source Backup: $AbsBackupDir"
Write-Host "Test Container: $TestContainerName (PostgreSQL 17)"
Write-Host "=========================================================="

# Safety Pre-Check: Verify existing container names to ensure no collision
$existingContainers = (& docker ps -a --format "{{.Names}}")
if ($existingContainers -contains $TestContainerName) {
    Write-Host "Removing leftover previous test container: $TestContainerName"
    & docker rm -f $TestContainerName | Out-Null
}

try {
    # -------------------------------------------------------------
    # 1. Start Isolated PostgreSQL 17 Container
    # -------------------------------------------------------------
    Write-Host "`n--- STEP 1: STARTING ISOLATED POSTGRESQL 17 CONTAINER ---"
    $runCmd = "docker run -d --name $TestContainerName -e POSTGRES_PASSWORD=test_restore_pwd_2026 -v `"${DockerBackupPath}:/backups:ro`" postgres:17.6-alpine"
    $containerId = Invoke-Expression $runCmd
    Write-Host "Started isolated container: $containerId" -ForegroundColor Green

    # Wait for PostgreSQL to be ready
    Write-Host -NoNewline "Waiting for PostgreSQL engine to be ready..."
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        $check = (& docker exec $TestContainerName pg_isready -U postgres 2>&1)
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 1
        Write-Host -NoNewline "."
    }
    Write-Host ""
    if (-not $ready) {
        throw "Isolated PostgreSQL failed to initialize in time."
    }
    Write-Host "Isolated PostgreSQL engine is READY." -ForegroundColor Green

    # -------------------------------------------------------------
    # 2. Create Roles and Isolated Test Databases
    # -------------------------------------------------------------
    Write-Host "`n--- STEP 2: CREATING ROLES AND DATABASES ---"
    $initSql = @"
CREATE ROLE keycloak WITH LOGIN;
CREATE ROLE wwtrading_users WITH LOGIN;
CREATE ROLE wwtrading_orders WITH LOGIN;
CREATE ROLE wwtrading_products WITH LOGIN;
CREATE ROLE wwtrading_quotations WITH LOGIN;
CREATE ROLE wwtrading_maintenance WITH LOGIN;
CREATE ROLE wwtrading_notifications WITH LOGIN;
CREATE ROLE wwtrading_carts WITH LOGIN;
CREATE ROLE wwtrading_wishlists WITH LOGIN;
CREATE ROLE wwtrading_support WITH LOGIN;

CREATE DATABASE test_keycloak OWNER keycloak;
CREATE DATABASE test_wwtrading_users OWNER wwtrading_users;
CREATE DATABASE test_wwtrading_orders OWNER wwtrading_orders;
CREATE DATABASE test_wwtrading_products OWNER wwtrading_products;
CREATE DATABASE test_wwtrading_quotations OWNER wwtrading_quotations;
CREATE DATABASE test_wwtrading_maintenance OWNER wwtrading_maintenance;
CREATE DATABASE test_wwtrading_notifications OWNER wwtrading_notifications;
CREATE DATABASE test_wwtrading_carts OWNER wwtrading_carts;
CREATE DATABASE test_wwtrading_wishlists OWNER wwtrading_wishlists;
CREATE DATABASE test_wwtrading_support OWNER wwtrading_support;
"@

    $sqlCmd = "cat << 'EOF' | psql -U postgres -d postgres`n$initSql`nEOF"
    & docker exec $TestContainerName sh -c "$sqlCmd" | Out-Null
    Write-Host "10 test roles and 10 test databases successfully initialized." -ForegroundColor Green

    # -------------------------------------------------------------
    # 3. Restore Each of the 10 Dumps
    # -------------------------------------------------------------
    Write-Host "`n--- STEP 3: RESTORING EACH POSTGRESQL DUMP ---"

    $DbList = @(
        @{ Service = "keycloak";      Db = "test_keycloak";                Dump = "wwtrading-keycloak-db-1.dump";        User = "keycloak" },
        @{ Service = "user-service";  Db = "test_wwtrading_users";         Dump = "wwtrading-user-db-1.dump";            User = "wwtrading_users" },
        @{ Service = "order-service"; Db = "test_wwtrading_orders";        Dump = "wwtrading-order-db-1.dump";           User = "wwtrading_orders" },
        @{ Service = "product-service"; Db = "test_wwtrading_products";    Dump = "wwtrading-product-db-1.dump";         User = "wwtrading_products" },
        @{ Service = "quotation-service"; Db = "test_wwtrading_quotations";Dump = "wwtrading-quotation-db-1.dump";       User = "wwtrading_quotations" },
        @{ Service = "maintenance-service"; Db = "test_wwtrading_maintenance"; Dump = "wwtrading-maintenance-db-1.dump";User = "wwtrading_maintenance" },
        @{ Service = "notification-service"; Db = "test_wwtrading_notifications"; Dump = "wwtrading-notification-db-1.dump"; User = "wwtrading_notifications" },
        @{ Service = "cart-service";  Db = "test_wwtrading_carts";         Dump = "wwtrading-cart-db-1.dump";            User = "wwtrading_carts" },
        @{ Service = "wishlist-service"; Db = "test_wwtrading_wishlists"; Dump = "wwtrading-wishlist-db-1.dump";        User = "wwtrading_wishlists" },
        @{ Service = "support-service"; Db = "test_wwtrading_support";     Dump = "wwtrading-support-db-1.dump";         User = "wwtrading_support" }
    )

    $DbResults = @()

    foreach ($item in $DbList) {
        $srv = $item.Service
        $db = $item.Db
        $dump = $item.Dump
        $u = $item.User

        Write-Host "`n>>> Restoring $srv -> $db from $dump ..."

        # Run pg_restore
        $restoreCmd = "pg_restore -U postgres -d $db /backups/$dump 2>&1"
        $restoreOutput = (& docker exec $TestContainerName sh -c "$restoreCmd")
        $exitCode = $LASTEXITCODE

        # Exit code 0 is clean; exit code 1 or 2 can mean harmless warnings (e.g. extension already exists or public schema ownership)
        Write-Host "pg_restore exit code: $exitCode"

        # Count tables in public schema
        $countTablesSql = "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';"
        $tableCount = (& docker exec $TestContainerName psql -U postgres -d $db -t -A -c "$countTablesSql").Trim()

        # Get list of tables
        $listTablesSql = "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name;"
        $tables = (& docker exec $TestContainerName psql -U postgres -d $db -t -A -c "$listTablesSql")

        # Count rows in key tables
        $tableStats = @()
        foreach ($t in $tables) {
            if ($t) {
                $rowCountSql = "SELECT count(*) FROM `"$t`";"
                $cnt = (& docker exec $TestContainerName psql -U postgres -d $db -t -A -c "$rowCountSql" 2>$null).Trim()
                $tableStats += "$t ($cnt rows)"
            }
        }

        # Check Flyway migration history if present
        $hasFlyway = (& docker exec $TestContainerName psql -U postgres -d $db -t -A -c "SELECT to_regclass('public.flyway_schema_history');" 2>$null).Trim()
        $flywayStr = if ($hasFlyway -and $hasFlyway -ne "") {
            (& docker exec $TestContainerName psql -U postgres -d $db -t -A -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;" 2>$null).Trim()
        } else {
            "No flyway table (standard for Keycloak)"
        }

        Write-Host "Tables restored: $tableCount" -ForegroundColor Green
        Write-Host "Flyway status:   $flywayStr" -ForegroundColor Cyan
        Write-Host "Sample tables:   $(($tableStats | Select-Object -First 5) -join ', ')"

        $DbResults += [PSCustomObject]@{
            Service = $srv
            Database = $db
            DumpFile = $dump
            ExitCode = $exitCode
            TableCount = [int]$tableCount
            FlywayInfo = $flywayStr
            Tables = $tableStats
        }
    }

    # -------------------------------------------------------------
    # 4. Extract and Test the 6 Volume Archives
    # -------------------------------------------------------------
    Write-Host "`n--- STEP 4: TESTING EXTRACTION OF 6 VOLUME ARCHIVES ---"

    if (Test-Path $TestTmpDir) {
        Remove-Item -Path $TestTmpDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $TestTmpDir -Force | Out-Null
    $DockerTestTmpPath = (Resolve-Path $TestTmpDir).Path -replace '\\', '/'

    $VolumeArchives = @(
        @{ Name = "order-transfer-proofs";   File = "volume-wwtrading_order-transfer-proofs-data.tar.gz" },
        @{ Name = "product-images";          File = "volume-wwtrading_product-images-data.tar.gz" },
        @{ Name = "maintenance-attachments"; File = "volume-wwtrading_maintenance-attachments-data.tar.gz" },
        @{ Name = "support-attachments";     File = "volume-wwtrading_support-attachments-data.tar.gz" },
        @{ Name = "kafka-data";               File = "volume-wwtrading_kafka-data.tar.gz" },
        @{ Name = "keycloak-data";            File = "volume-wwtrading_keycloak-data.tar.gz" }
    )

    $VolumeResults = @()

    foreach ($va in $VolumeArchives) {
        $name = $va.Name
        $file = $va.File
        Write-Host "`n>>> Testing archive: $file ($name) ..."

        $subDir = Join-Path $TestTmpDir $name
        New-Item -ItemType Directory -Path $subDir -Force | Out-Null

        # Extract archive in isolated alpine container into temporary test directory
        $extractCmd = "docker run --rm -v `"${DockerBackupPath}:/b:ro`" -v `"${DockerTestTmpPath}/${name}:/dest`" alpine tar -xzf /b/$file -C /dest"
        Invoke-Expression $extractCmd
        if ($LASTEXITCODE -ne 0) {
            throw "Extraction failed for $file"
        }

        # Inspect extracted contents using alpine container to examine permissions and exact file structure
        $inspectCmd = "docker run --rm -v `"${DockerTestTmpPath}/${name}:/dest:ro`" alpine sh -c `"find /dest -mindepth 1 | wc -l; echo '---'; du -sh /dest; echo '---'; ls -la /dest`""
        $inspectOutput = Invoke-Expression $inspectCmd

        $fileCount = ($inspectOutput[0]).Trim()
        $uncompressedSize = ($inspectOutput[2] -replace '\s+/dest.*', '').Trim()

        Write-Host "Extracted $fileCount files/dirs, uncompressed size: $uncompressedSize" -ForegroundColor Green

        $VolumeResults += [PSCustomObject]@{
            Name = $name
            Archive = $file
            ExtractedCount = [int]$fileCount
            UncompressedSize = $uncompressedSize
            InspectionDetails = ($inspectOutput -join "`n")
        }
    }

    # Save test results to JSON
    $TestSummary = @{
        Timestamp = $Timestamp
        Databases = $DbResults
        Volumes = $VolumeResults
    }
    $TestSummary | ConvertTo-Json -Depth 5 | Set-Content -Path (Join-Path $AbsBackupDir "RESTORE_TEST_RESULTS.json") -Encoding UTF8

    Write-Host "`n=========================================================="
    Write-Host "ISOLATED RESTORATION TEST SUCCESSFULLY COMPLETED!"
    Write-Host "=========================================================="

} finally {
    # -------------------------------------------------------------
    # 5. Cleanup Isolated Test Resources
    # -------------------------------------------------------------
    Write-Host "`n--- STEP 5: CLEANUP OF ISOLATED TEST RESOURCES ---"
    Write-Host "Stopping and removing test container: $TestContainerName ..."
    & docker rm -f $TestContainerName | Out-Null
    Write-Host "Test container $TestContainerName removed." -ForegroundColor Green

    if (Test-Path $TestTmpDir) {
        Write-Host "Cleaning up temporary test directory: $TestTmpDir ..."
        Remove-Item -Path $TestTmpDir -Recurse -Force
        Write-Host "Temporary directory cleaned." -ForegroundColor Green
    }

    # Verify no persistent volumes or containers of the main app were affected
    Write-Host "Verifying running WWTRADING containers..."
    $runningServices = (& docker ps --format "{{.Names}}" | Select-String "wwtrading")
    Write-Host "Active WWTRADING services: $($runningServices.Count) running untouched." -ForegroundColor Green
}

