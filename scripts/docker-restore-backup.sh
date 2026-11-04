#!/usr/bin/env bash
# ==============================================================================
# WWTRADING — Controlled PostgreSQL Backup Restoration Tool (Docker)
# ==============================================================================
# Usage:
#   ./scripts/docker-restore-backup.sh --check
#   ./scripts/docker-restore-backup.sh --service order-db
#   ./scripts/docker-restore-backup.sh --all --confirm
#   ./scripts/docker-restore-backup.sh --help
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_DIR="$PROJECT_ROOT/backups/full-backup-20261103-1020"

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Database registry: service_name:db_name:user_name:dump_filename
DATABASES=(
    "keycloak-db:keycloak:keycloak:wwtrading-keycloak-db-1.dump"
    "user-db:wwtrading_users:wwtrading_users:wwtrading-user-db-1.dump"
    "order-db:wwtrading_orders:wwtrading_orders:wwtrading-order-db-1.dump"
    "product-db:wwtrading_products:wwtrading_products:wwtrading-product-db-1.dump"
    "quotation-db:wwtrading_quotations:wwtrading_quotations:wwtrading-quotation-db-1.dump"
    "maintenance-db:wwtrading_maintenance:wwtrading_maintenance:wwtrading-maintenance-db-1.dump"
    "notification-db:wwtrading_notifications:wwtrading_notifications:wwtrading-notification-db-1.dump"
    "cart-db:wwtrading_carts:wwtrading_carts:wwtrading-cart-db-1.dump"
    "wishlist-db:wwtrading_wishlists:wwtrading_wishlists:wwtrading-wishlist-db-1.dump"
    "support-db:wwtrading_support:wwtrading_support:wwtrading-support-db-1.dump"
)

TARGET_SERVICE=""
CONFIRMED=false
CHECK_ONLY=false

print_usage() {
    echo -e "${BOLD}Usage:${NC} $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --check                 Validate all backup dumps without executing any restore"
    echo "  --service <name>        Restore a specific service database (e.g. order-db, product-db)"
    echo "  --all                   Restore all 10 databases"
    echo "  --confirm               Bypass manual interactive confirmation prompt"
    echo "  --help                  Show this help message"
    echo ""
    echo "Available services:"
    for entry in "${DATABASES[@]}"; do
        IFS=":" read -r svc db user dump <<< "$entry"
        echo "  - $svc (DB: $db -> Dump: $dump)"
    done
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --check)
            CHECK_ONLY=true
            shift
            ;;
        --service)
            TARGET_SERVICE="$2"
            shift 2
            ;;
        --all)
            TARGET_SERVICE="all"
            shift
            ;;
        --confirm)
            CONFIRMED=true
            shift
            ;;
        --help|-h)
            print_usage
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

if [ "$CHECK_ONLY" = true ]; then
    echo -e "${BLUE}${BOLD}=== WWTRADING Backup Integrity Audit ===${NC}"
    echo "Scanning directory: $BACKUP_DIR"
    echo ""
    all_ok=true
    for entry in "${DATABASES[@]}"; do
        IFS=":" read -r svc db user dump <<< "$entry"
        dump_path="$BACKUP_DIR/$dump"
        if [ ! -f "$dump_path" ]; then
            echo -e "  [${RED}MISSING${NC}] $dump (Target: $db)"
            all_ok=false
            continue
        fi
        magic=$(head -c 5 "$dump_path" 2>/dev/null || true)
        size=$(wc -c < "$dump_path" | tr -d ' ')
        if [ "$magic" == "PGDMP" ]; then
            echo -e "  [${GREEN}VALID PGDMP${NC}] $dump -> DB: $db (${size} bytes)"
        else
            echo -e "  [${RED}INVALID FORMAT${NC}] $dump (Magic: '$magic')"
            all_ok=false
        fi
    done
    echo ""
    if [ "$all_ok" = true ]; then
        echo -e "${GREEN}${BOLD}Audit complete: All 10 dumps are present and verified in PostgreSQL Custom Format.${NC}"
    else
        echo -e "${RED}${BOLD}Audit complete: Errors found above.${NC}"
    fi
    exit 0
fi

if [ -z "$TARGET_SERVICE" ]; then
    echo -e "${RED}Error: You must specify --service <name> or --all.${NC}"
    print_usage
    exit 1
fi

restore_single_db() {
    local svc="$1"
    local db="$2"
    local user="$3"
    local dump="$4"
    local dump_path="$BACKUP_DIR/$dump"

    echo ""
    echo -e "${BLUE}${BOLD}>>> Processing Service: $svc (Database: $db) <<<${NC}"

    if [ ! -f "$dump_path" ]; then
        echo -e "${RED}[ERROR] Dump file '$dump_path' not found.${NC}"
        return 1
    fi

    # Verify container is running
    local container_id
    container_id=$(docker compose ps -q "$svc" 2>/dev/null || true)
    if [ -z "$container_id" ]; then
        echo -e "${RED}[ERROR] Container for service '$svc' is not running. Start it first.${NC}"
        return 1
    fi

    # Check existing tables in target DB (Strict Protection)
    local table_count
    table_count=$(docker compose exec -T "$svc" psql -U "$user" -d "$db" -t -A -c "
        SELECT count(*) FROM information_schema.tables 
        WHERE table_schema = 'public' 
          AND table_name NOT LIKE 'pg_%';
    " 2>/dev/null || echo "0")

    if [ "$table_count" -gt 0 ]; then
        echo -e "${RED}[STRICT PROTECTION] Database '$db' already contains $table_count table(s).${NC}"
        echo -e "${RED}Restoration is strictly BLOCKED to protect existing data from corruption, duplicate key errors, or overwrite.${NC}"
        echo -e "${RED}Backups can only be restored into a fresh, uninitialized database.${NC}"
        echo -e "${YELLOW}No DROP, TRUNCATE, or volume modification is permitted by this tool.${NC}"
        return 1
    fi

    # Check if marker already exists
    local marker_count
    marker_count=$(docker compose exec -T "$svc" psql -U "$user" -d "$db" -t -A -c "
        SELECT count(*) FROM information_schema.tables 
        WHERE table_schema = 'public' 
          AND table_name = '_wwtrading_backup_restore_marker';
    " 2>/dev/null || echo "0")

    if [ "$marker_count" -gt 0 ]; then
        echo -e "${YELLOW}[STRICT PROTECTION] Database '$db' has already been restored previously (_wwtrading_backup_restore_marker detected).${NC}"
        echo -e "${RED}Duplicate restoration is blocked.${NC}"
        return 1
    fi

    echo -e "${GREEN}[OK] Database '$db' is clean and uninitialized (0 table). Proceeding with restoration.${NC}"

    # Execute pg_restore via container
    echo "Streaming $(basename "$dump_path") into container '$svc'..."
    docker compose exec -T "$svc" pg_restore \
        -U "$user" \
        -d "$db" \
        --no-owner \
        --no-privileges \
        "/backups/$dump" || {
            # In case /backups mount is not present, stream directly from host:
            echo "Falling back to stdin stream from host..."
            docker compose exec -T "$svc" pg_restore -U "$user" -d "$db" --no-owner --no-privileges < "$dump_path"
        }

    # Record persistent marker
    docker compose exec -T "$svc" psql -U "$user" -d "$db" -c "
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
        VALUES ('$dump', '$db', '$user', (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name NOT LIKE 'pg_%' AND table_name != '_wwtrading_backup_restore_marker'), 'COMPLETED');
    " >/dev/null

    local final_count
    final_count=$(docker compose exec -T "$svc" psql -U "$user" -d "$db" -t -A -c "
        SELECT count(*) FROM information_schema.tables 
        WHERE table_schema = 'public' 
          AND table_name NOT LIKE 'pg_%' 
          AND table_name != '_wwtrading_backup_restore_marker';
    ")

    echo -e "${GREEN}[SUCCESS] Restored $dump into $db. Total tables: $final_count.${NC}"
}

# Confirmation prompt if --confirm was not passed
if [ "$CONFIRMED" != true ]; then
    echo -e "${YELLOW}${BOLD}=== SAFETY CONFIRMATION REQUIRED ===${NC}"
    echo "You requested restoration for: ${BOLD}$TARGET_SERVICE${NC}"
    echo "Restoring backups into databases will modify table data."
    echo ""
    read -r -p "Type 'RESTORE' in uppercase to confirm: " user_input
    if [ "$user_input" != "RESTORE" ]; then
        echo -e "${RED}Restoration cancelled by user.${NC}"
        exit 0
    fi
    CONFIRMED=true
fi

# Execute restoration
if [ "$TARGET_SERVICE" == "all" ]; then
    for entry in "${DATABASES[@]}"; do
        IFS=":" read -r svc db user dump <<< "$entry"
        restore_single_db "$svc" "$db" "$user" "$dump"
    done
else
    matched=false
    for entry in "${DATABASES[@]}"; do
        IFS=":" read -r svc db user dump <<< "$entry"
        if [ "$svc" == "$TARGET_SERVICE" ] || [ "$db" == "$TARGET_SERVICE" ]; then
            matched=true
            restore_single_db "$svc" "$db" "$user" "$dump"
            break
        fi
    done
    if [ "$matched" != true ]; then
        echo -e "${RED}Service or DB '$TARGET_SERVICE' not recognized.${NC}"
        print_usage
        exit 1
    fi
fi

echo ""
echo -e "${GREEN}${BOLD}=== Restoration operation finished successfully ===${NC}"

