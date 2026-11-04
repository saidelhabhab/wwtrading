# WWTRADING - Live Inventory Read-Only Inspection Script

$dbs = @(
    @{ Container = 'wwtrading-keycloak-db-1'; Db = 'keycloak'; User = 'keycloak' },
    @{ Container = 'wwtrading-user-db-1'; Db = 'wwtrading_users'; User = 'wwtrading_users' },
    @{ Container = 'wwtrading-order-db-1'; Db = 'wwtrading_orders'; User = 'wwtrading_orders' },
    @{ Container = 'wwtrading-product-db-1'; Db = 'wwtrading_products'; User = 'wwtrading_products' },
    @{ Container = 'wwtrading-quotation-db-1'; Db = 'wwtrading_quotations'; User = 'wwtrading_quotations' },
    @{ Container = 'wwtrading-maintenance-db-1'; Db = 'wwtrading_maintenance'; User = 'wwtrading_maintenance' },
    @{ Container = 'wwtrading-notification-db-1'; Db = 'wwtrading_notifications'; User = 'wwtrading_notifications' },
    @{ Container = 'wwtrading-cart-db-1'; Db = 'wwtrading_carts'; User = 'wwtrading_carts' },
    @{ Container = 'wwtrading-wishlist-db-1'; Db = 'wwtrading_wishlists'; User = 'wwtrading_wishlists' },
    @{ Container = 'wwtrading-support-db-1'; Db = 'wwtrading_support'; User = 'wwtrading_support' }
)

$report = @()
foreach ($item in $dbs) {
    $c = $item.Container
    $d = $item.Db
    $u = $item.User
    
    $tableCount = (& docker exec $c psql -U $u -d $d -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';" 2>$null).Trim()
    $seqCount = (& docker exec $c psql -U $u -d $d -t -A -c "SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'public';" 2>$null).Trim()
    $constraintCount = (& docker exec $c psql -U $u -d $d -t -A -c "SELECT count(*) FROM information_schema.table_constraints WHERE constraint_schema = 'public';" 2>$null).Trim()
    $indexCount = (& docker exec $c psql -U $u -d $d -t -A -c "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public';" 2>$null).Trim()
    
    $tables = (& docker exec $c psql -U $u -d $d -t -A -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name;" 2>$null)
    
    $tableRows = [ordered]@{}
    foreach ($t in $tables) {
        if ($t) {
            $cnt = (& docker exec $c psql -U $u -d $d -t -A -c "SELECT count(*) FROM `"$t`";" 2>$null).Trim()
            $tableRows[$t] = [int]$cnt
        }
    }
    
    $hasFlyway = (& docker exec $c psql -U $u -d $d -t -A -c "SELECT to_regclass('public.flyway_schema_history');" 2>$null).Trim()
    $flywayVersion = if ($hasFlyway) {
        (& docker exec $c psql -U $u -d $d -t -A -c "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;" 2>$null).Trim()
    } else { 'N/A' }

    $report += [PSCustomObject]@{
        Database = $d
        Container = $c
        User = $u
        Tables = [int]$tableCount
        Sequences = [int]$seqCount
        Constraints = [int]$constraintCount
        Indexes = [int]$indexCount
        Flyway = $flywayVersion
        TableRows = $tableRows
    }
}

$report | ConvertTo-Json -Depth 5 | Set-Content -Path "scripts/live-inventory.json" -Encoding UTF8
Write-Host "Live inventory saved to scripts/live-inventory.json"

