# WWTRADING - Isolated Environment Test Script (Phase 2 & 3)
# Uses compose.test.yaml, project name: wwtest
# Tests restored databases, infrastructure, and application microservices.

$ErrorActionPreference = "Stop"

$BackupDir = "C:\Users\hp\Documents\Codex\wwtriding\backups\docker-export-20261103-134923"
$DockerBackupPath = $BackupDir -replace '\\', '/'

Write-Host "=========================================================="
Write-Host "WWTRADING - STARTING ISOLATED ENVIRONMENT TEST (wwtest)"
Write-Host "Backup Source: $BackupDir"
Write-Host "=========================================================="

# 1. Pre-populate 4 media/upload volumes from retained backup archives
$MediaVolumes = @(
    @{ Volume = "wwtest_order-transfer-proofs-data";   Archive = "volume-wwtrading_order-transfer-proofs-data.tar.gz" },
    @{ Volume = "wwtest_product-images-data";          Archive = "volume-wwtrading_product-images-data.tar.gz" },
    @{ Volume = "wwtest_maintenance-attachments-data"; Archive = "volume-wwtrading_maintenance-attachments-data.tar.gz" },
    @{ Volume = "wwtest_support-attachments-data";     Archive = "volume-wwtrading_support-attachments-data.tar.gz" }
)

Write-Host "`n--- STEP 1: PRE-CREATING AND POPULATING MEDIA VOLUMES ---"
foreach ($mv in $MediaVolumes) {
    $volName = $mv.Volume
    $arch = $mv.Archive
    Write-Host -NoNewline "Populating $volName from $arch ... "
    
    # Create volume if not exists
    & docker volume create $volName | Out-Null
    
    # Extract archive into volume using temporary alpine container
    $extractCmd = "docker run --rm -v ${volName}:/v_target -v `"${DockerBackupPath}:/backup:ro`" alpine tar -xzf /backup/$arch -C /v_target"
    $res = Invoke-Expression $extractCmd 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED!" -ForegroundColor Red
        throw ("Failed to populate " + $volName + ": " + $res)
    }
    Write-Host "OK" -ForegroundColor Green
}

# 2. Start the 10 PostgreSQL databases
Write-Host "`n--- STEP 2: STARTING 10 ISOLATED POSTGRESQL DATABASES ---"
& docker compose -f compose.test.yaml up -d keycloak-db user-db quotation-db order-db cart-db wishlist-db product-db maintenance-db support-db notification-db
if ($LASTEXITCODE -ne 0) { throw "Failed to start database containers." }

Write-Host "Waiting for 10 databases to become healthy and auto-restore dumps..."
$dbs = @(
    "wwtest-keycloak-db-1", "wwtest-user-db-1", "wwtest-quotation-db-1", "wwtest-order-db-1",
    "wwtest-cart-db-1", "wwtest-wishlist-db-1", "wwtest-product-db-1", "wwtest-maintenance-db-1",
    "wwtest-support-db-1", "wwtest-notification-db-1"
)

foreach ($c in $dbs) {
    $healthy = $false
    for ($i = 0; $i -lt 30; $i++) {
        $status = & docker inspect $c --format '{{.State.Health.Status}}' 2>$null
        if ($status -eq "healthy") {
            $healthy = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if ($healthy) {
        Write-Host "  $c : HEALTHY" -ForegroundColor Green
    } else {
        Write-Host "  $c : TIMEOUT OR UNHEALTHY" -ForegroundColor Red
        throw ("Database container " + $c + " failed health check.")
    }
}

# 3. Verify database table restoration
Write-Host "`n--- STEP 3: VERIFYING RESTORED TABLES IN ALL 10 DATABASES ---"
$DbVerifications = @(
    @{ Container = "wwtest-keycloak-db-1";     Db = "keycloak";                User = "keycloak" },
    @{ Container = "wwtest-user-db-1";         Db = "wwtrading_users";         User = "wwtrading_users" },
    @{ Container = "wwtest-quotation-db-1";    Db = "wwtrading_quotations";    User = "wwtrading_quotations" },
    @{ Container = "wwtest-order-db-1";        Db = "wwtrading_orders";        User = "wwtrading_orders" },
    @{ Container = "wwtest-cart-db-1";         Db = "wwtrading_carts";         User = "wwtrading_carts" },
    @{ Container = "wwtest-wishlist-db-1";     Db = "wwtrading_wishlists";     User = "wwtrading_wishlists" },
    @{ Container = "wwtest-product-db-1";      Db = "wwtrading_products";      User = "wwtrading_products" },
    @{ Container = "wwtest-maintenance-db-1";  Db = "wwtrading_maintenance";   User = "wwtrading_maintenance" },
    @{ Container = "wwtest-support-db-1";      Db = "wwtrading_support";       User = "wwtrading_support" },
    @{ Container = "wwtest-notification-db-1"; Db = "wwtrading_notifications"; User = "wwtrading_notifications" }
)

$DbStatusReport = @()
foreach ($db in $DbVerifications) {
    $c = $db.Container
    $d = $db.Db
    $u = $db.User
    
    $query = "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name NOT LIKE 'pg_%';"
    $tableCount = (& docker exec $c psql -U $u -d $d -t -A -c "$query").Trim()
    
    $markerQuery = "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '_wwtrading_backup_restore_marker';"
    $hasMarker = (& docker exec $c psql -U $u -d $d -t -A -c "$markerQuery").Trim()
    
    $pass = ([int]$tableCount -gt 0)
    Write-Host ("  " + $d + " (" + $c + "): " + $tableCount + " tables, marker=" + $hasMarker) -ForegroundColor Green
    
    $DbStatusReport += [PSCustomObject]@{
        Service = $c
        Database = $d
        TablesCount = $tableCount
        MarkerPresent = ($hasMarker -eq "1")
        Status = if ($pass) { "PASS" } else { "FAIL" }
    }
}

# 4. Start Core Infrastructure (Mailhog, Kafka, Discovery, Config Server, Keycloak)
Write-Host "`n--- STEP 4: STARTING CORE INFRASTRUCTURE SERVICES ---"
& docker compose -f compose.test.yaml up -d mailhog kafka discovery-server config-server keycloak
if ($LASTEXITCODE -ne 0) { throw "Failed to start infrastructure containers." }

Write-Host "Waiting for discovery-server, config-server, kafka, and keycloak to become healthy..."
$infraServices = @("wwtest-discovery-server-1", "wwtest-config-server-1", "wwtest-kafka-1", "wwtest-keycloak-1")
foreach ($c in $infraServices) {
    $healthy = $false
    for ($i = 0; $i -lt 40; $i++) {
        $status = & docker inspect $c --format '{{.State.Health.Status}}' 2>$null
        if ($status -eq "healthy") {
            $healthy = $true
            break
        }
        Start-Sleep -Seconds 3
    }
    if ($healthy) {
        Write-Host "  $c : HEALTHY" -ForegroundColor Green
    } else {
        Write-Host "  $c : TIMEOUT OR UNHEALTHY ($status)" -ForegroundColor Red
        throw ("Infrastructure container " + $c + " failed health check.")
    }
}

# 5. Start Application Microservices & API Gateway
Write-Host "`n--- STEP 5: STARTING APPLICATION MICROSERVICES & GATEWAY ---"
& docker compose -f compose.test.yaml up -d user-service product-service order-service quotation-service cart-service wishlist-service maintenance-service notification-service support-service api-gateway
if ($LASTEXITCODE -ne 0) { throw "Failed to start application services." }

Write-Host "Waiting 30 seconds for microservices to initialize and register with Eureka..."
Start-Sleep -Seconds 30

# 6. Test Connectivity & Health
Write-Host "`n--- STEP 6: TESTING SERVICE HEALTH AND CONNECTIVITY ---"
$ServiceTests = @()

# Test Keycloak on 18180
try {
    $kcRes = Invoke-WebRequest -Uri "http://localhost:18180/health/ready" -UseBasicParsing -TimeoutSec 5
    $kcPass = ($kcRes.StatusCode -eq 200)
} catch {
    $kcPass = $false
}
$ServiceTests += [PSCustomObject]@{ Service = "Keycloak (Port 18180)"; Endpoint = "/health/ready"; Result = if ($kcPass) { "PASS" } else { "FAIL" } }
Write-Host ("  Keycloak (Port 18180): " + $(if ($kcPass) { "PASS" } else { "FAIL" }))

# Test Discovery Server on 18761
try {
    $discRes = Invoke-WebRequest -Uri "http://localhost:18761/actuator/health" -UseBasicParsing -TimeoutSec 5
    $discPass = ($discRes.StatusCode -eq 200)
} catch {
    $discPass = $false
}
$ServiceTests += [PSCustomObject]@{ Service = "Discovery Server (Port 18761)"; Endpoint = "/actuator/health"; Result = if ($discPass) { "PASS" } else { "FAIL" } }
Write-Host ("  Discovery Server (Port 18761): " + $(if ($discPass) { "PASS" } else { "FAIL" }))

# Test Config Server on 18888
try {
    $cfgRes = Invoke-WebRequest -Uri "http://localhost:18888/actuator/health" -UseBasicParsing -TimeoutSec 5
    $cfgPass = ($cfgRes.StatusCode -eq 200)
} catch {
    $cfgPass = $false
}
$ServiceTests += [PSCustomObject]@{ Service = "Config Server (Port 18888)"; Endpoint = "/actuator/health"; Result = if ($cfgPass) { "PASS" } else { "FAIL" } }
Write-Host ("  Config Server (Port 18888): " + $(if ($cfgPass) { "PASS" } else { "FAIL" }))

# Test API Gateway on 18080
try {
    $gwRes = Invoke-WebRequest -Uri "http://localhost:18080/actuator/health" -UseBasicParsing -TimeoutSec 5
    $gwPass = ($gwRes.StatusCode -eq 200)
} catch {
    $gwPass = $false
}
$ServiceTests += [PSCustomObject]@{ Service = "API Gateway (Port 18080)"; Endpoint = "/actuator/health"; Result = if ($gwPass) { "PASS" } else { "FAIL" } }
Write-Host ("  API Gateway (Port 18080): " + $(if ($gwPass) { "PASS" } else { "FAIL" }))

# Test Mailhog on 18025
try {
    $mhRes = Invoke-WebRequest -Uri "http://localhost:18025/" -UseBasicParsing -TimeoutSec 5
    $mhPass = ($mhRes.StatusCode -eq 200)
} catch {
    $mhPass = $false
}
$ServiceTests += [PSCustomObject]@{ Service = "Mailhog UI (Port 18025)"; Endpoint = "/"; Result = if ($mhPass) { "PASS" } else { "FAIL" } }
Write-Host ("  Mailhog UI (Port 18025): " + $(if ($mhPass) { "PASS" } else { "FAIL" }))

# Check all microservices running in Docker
$Microservices = @(
    "wwtest-user-service-1", "wwtest-product-service-1", "wwtest-order-service-1",
    "wwtest-quotation-service-1", "wwtest-cart-service-1", "wwtest-wishlist-service-1",
    "wwtest-maintenance-service-1", "wwtest-notification-service-1", "wwtest-support-service-1"
)

foreach ($ms in $Microservices) {
    $state = & docker inspect $ms --format '{{.State.Status}}' 2>$null
    $isUp = ($state -eq "running")
    $ServiceTests += [PSCustomObject]@{ Service = $ms; Endpoint = "Docker State"; Result = if ($isUp) { "PASS" } else { "FAIL" } }
    Write-Host ("  " + $ms + ": " + $(if ($isUp) { "PASS (running)" } else { "FAIL ($state)" }))
}

Write-Host "`n=========================================================="
Write-Host "ISOLATED ENVIRONMENT TEST COMPLETED"
Write-Host "=========================================================="
$DbStatusReport | Format-Table -AutoSize
$ServiceTests | Format-Table -AutoSize

