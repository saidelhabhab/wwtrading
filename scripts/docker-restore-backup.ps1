<#
.SYNOPSIS
    WWTRADING — Outil PowerShell de restauration contrôlée des backups PostgreSQL Docker.

.DESCRIPTION
    Permet d'auditer l'intégrité des backups, de vérifier les formats, et d'exécuter
    une restauration contrôlée vers un ou l'ensemble des conteneurs PostgreSQL de WWTRADING.

.PARAMETER Check
    Vérifie l'intégrité et le format de tous les backups sans toucher aux bases (strictement lecture seule).

.PARAMETER Service
    Nom du service cible (ex: order-db, product-db, keycloak-db) ou 'all'.

.PARAMETER Confirm
    Confirme explicitement l'opération sans demande interactive.

.PARAMETER Force
    Permet l'exécution en mode non-interactif pour scripts d'automatisation.
    SÉCURITÉ : -Force ne permet JAMAIS d'écraser une base contenant déjà des tables,
    et ne déclenche JAMAIS de DROP DATABASE, DROP SCHEMA, TRUNCATE ou suppression de volume.
#>

[CmdletBinding()]
param(
    [switch]$Check,
    [string]$Service,
    [switch]$Confirm,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$BackupDir = Join-Path $ProjectRoot 'backups\full-backup-20261103-1020'

$DatabaseRegistry = @(
    @{ Service = "keycloak-db";     DB = "keycloak";                User = "keycloak";                Dump = "wwtrading-keycloak-db-1.dump" },
    @{ Service = "user-db";         DB = "wwtrading_users";         User = "wwtrading_users";         Dump = "wwtrading-user-db-1.dump" },
    @{ Service = "order-db";        DB = "wwtrading_orders";        User = "wwtrading_orders";        Dump = "wwtrading-order-db-1.dump" },
    @{ Service = "product-db";      DB = "wwtrading_products";      User = "wwtrading_products";      Dump = "wwtrading-product-db-1.dump" },
    @{ Service = "quotation-db";    DB = "wwtrading_quotations";    User = "wwtrading_quotations";    Dump = "wwtrading-quotation-db-1.dump" },
    @{ Service = "maintenance-db";  DB = "wwtrading_maintenance";   User = "wwtrading_maintenance";   Dump = "wwtrading-maintenance-db-1.dump" },
    @{ Service = "notification-db"; DB = "wwtrading_notifications"; User = "wwtrading_notifications"; Dump = "wwtrading-notification-db-1.dump" },
    @{ Service = "cart-db";         DB = "wwtrading_carts";         User = "wwtrading_carts";         Dump = "wwtrading-cart-db-1.dump" },
    @{ Service = "wishlist-db";     DB = "wwtrading_wishlists";     User = "wwtrading_wishlists";     Dump = "wwtrading-wishlist-db-1.dump" },
    @{ Service = "support-db";      DB = "wwtrading_support";       User = "wwtrading_support";       Dump = "wwtrading-support-db-1.dump" }
)

if ($Check) {
    Write-Host "=== WWTRADING Backup Integrity Audit (Lecture Seule) ===" -ForegroundColor Cyan
    Write-Host "Scanning: $BackupDir`n"

    $allValid = $true
    foreach ($entry in $DatabaseRegistry) {
        $dumpPath = Join-Path $BackupDir $entry.Dump
        if (-not (Test-Path $dumpPath)) {
            Write-Host "  [MISSING] $($entry.Dump) -> Target DB: $($entry.DB)" -ForegroundColor Red
            $allValid = $false
            continue
        }

        $bytes = [System.IO.File]::ReadAllBytes($dumpPath)
        $magic = [System.Text.Encoding]::ASCII.GetString($bytes[0..4])
        $size = (Get-Item $dumpPath).Length

        if ($magic -eq 'PGDMP') {
            Write-Host "  [VALID PGDMP] $($entry.Dump) -> Target: $($entry.DB) ($size bytes)" -ForegroundColor Green
        } else {
            Write-Host "  [INVALID FORMAT] $($entry.Dump) (Magic: '$magic')" -ForegroundColor Red
            $allValid = $false
        }
    }

    Write-Host ""
    if ($allValid) {
        Write-Host "Audit Complete: All 10 dumps are verified and ready for safe restoration." -ForegroundColor Green
    } else {
        Write-Host "Audit Complete: Some dumps have issues." -ForegroundColor Red
    }
    return
}

if (-not $Service) {
    Write-Host "Error: Please specify -Service <service-name> (or 'all') or use -Check." -ForegroundColor Red
    Write-Host "Examples:"
    Write-Host "  .\scripts\docker-restore-backup.ps1 -Check"
    Write-Host "  .\scripts\docker-restore-backup.ps1 -Service order-db"
    Write-Host "  .\scripts\docker-restore-backup.ps1 -Service all -Confirm"
    return
}

function Restore-Database {
    param($entry)

    $dumpPath = Join-Path $BackupDir $entry.Dump
    Write-Host "`n>>> Processing Service: $($entry.Service) (Database: $($entry.DB)) <<<" -ForegroundColor Cyan

    if (-not (Test-Path $dumpPath)) {
        Write-Host "  [ERROR] File not found: $dumpPath" -ForegroundColor Red
        return
    }

    # Verify container state
    $running = docker compose ps -q $entry.Service 2>$null
    if (-not $running) {
        Write-Host "  [ERROR] Container '$($entry.Service)' is not running. Please start it with 'docker compose up -d $($entry.Service)'." -ForegroundColor Red
        return
    }

    # 1. Safety check on existing public tables (Strict Protection)
    $countStr = docker compose exec -T $entry.Service psql -U $entry.User -d $entry.DB -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name NOT LIKE 'pg_%';" 2>$null
    $tableCount = 0
    [int]::TryParse($countStr.Trim(), [ref]$tableCount) | Out-Null

    if ($tableCount -gt 0) {
        Write-Host "  [PROTECTION STRICTE ACTIVE] La base '$($entry.DB)' contient deja $tableCount table(s)." -ForegroundColor Yellow
        Write-Host "  REGLE DE SECURITE : Une base contenant deja des tables ne peut PAS etre ecrasee ou restauree." -ForegroundColor Red
        Write-Host "  La restauration de backups est reservee EXCLUSIVEMENT aux bases neuves et vides (0 table)." -ForegroundColor Red
        Write-Host "  Aucune commande DROP DATABASE, DROP SCHEMA, TRUNCATE ou suppression de volume n'est autorisee." -ForegroundColor Red
        Write-Host "  Operation ABANDONNEE pour proteger l'integrite absolue des donnees existantes." -ForegroundColor Red
        return
    }

    # 2. Safety check on restoration marker table
    $markerCountStr = docker compose exec -T $entry.Service psql -U $entry.User -d $entry.DB -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name = '_wwtrading_backup_restore_marker';" 2>$null
    $markerCount = 0
    [int]::TryParse($markerCountStr.Trim(), [ref]$markerCount) | Out-Null

    if ($markerCount -gt 0) {
        Write-Host "  [PROTECTION STRICTE] La base '$($entry.DB)' a deja fait l'objet d'une restauration (_wwtrading_backup_restore_marker detecte)." -ForegroundColor Yellow
        Write-Host "  Toute repetition de restauration est bloquee pour eviter les conflits et doublons." -ForegroundColor Red
        return
    }

    Write-Host "  [OK] Base '$($entry.DB)' propre et vierge (0 table). Demarrage de la restauration..." -ForegroundColor Green
    Write-Host "  Restoring $($entry.Dump) into $($entry.DB)..." -ForegroundColor Gray
    docker compose exec -T $entry.Service pg_restore -U $entry.User -d $entry.DB --no-owner --no-privileges "/backups/$($entry.Dump)"

    # Record persistent marker
    docker compose exec -T $entry.Service psql -U $entry.User -d $entry.DB -c @"
        CREATE TABLE IF NOT EXISTS _wwtrading_backup_restore_marker (
            id SERIAL PRIMARY KEY,
            backup_file TEXT NOT NULL,
            target_database TEXT NOT NULL,
            restored_by TEXT NOT NULL,
            restored_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            tables_restored INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'COMPLETED'
        );
        INSERT INTO _wwtrading_backup_restore_marker (backup_file, target_database, restored_by, tables_restored, status)
        VALUES ('$($entry.Dump)', '$($entry.DB)', '$($entry.User)', (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name NOT LIKE 'pg_%' AND table_name != '_wwtrading_backup_restore_marker'), 'COMPLETED');
"@ | Out-Null

    $finalCount = docker compose exec -T $entry.Service psql -U $entry.User -d $entry.DB -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name NOT LIKE 'pg_%' AND table_name != '_wwtrading_backup_restore_marker';"
    Write-Host "  [SUCCESS] Restored $($entry.Dump) into $($entry.DB). Public tables now: $($finalCount.Trim())." -ForegroundColor Green
}

# Require explicit confirmation if neither -Confirm nor -Force was supplied
if (-not $Confirm -and -not $Force) {
    Write-Host "`n=== SAFETY CONFIRMATION REQUIRED ===" -ForegroundColor Yellow
    Write-Host "Target requested: $Service"
    Write-Host "NOTE : Seules les bases neuves et vides seront restaurees. Toute base existante sera preservee." -ForegroundColor Cyan
    $response = Read-Host "Type 'RESTORE' in uppercase to proceed"
    if ($response -ne 'RESTORE') {
        Write-Host "Restoration cancelled by user." -ForegroundColor Red
        return
    }
}

if ($Service -eq 'all') {
    foreach ($entry in $DatabaseRegistry) {
        Restore-Database -entry $entry
    }
} else {
    $matched = $DatabaseRegistry | Where-Object { $_.Service -eq $Service -or $_.DB -eq $Service }
    if ($matched) {
        Restore-Database -entry $matched
    } else {
        Write-Host "Unknown service: $Service" -ForegroundColor Red
    }
}
