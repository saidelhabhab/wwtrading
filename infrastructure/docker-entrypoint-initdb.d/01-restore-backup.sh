#!/bin/sh
set -e

# ==============================================================================
# WWTRADING — Automated & Secure PostgreSQL Backup Restore (Docker Entrypoint)
# ==============================================================================
# This script is invoked during fresh PostgreSQL container initialization
# via /docker-entrypoint-initdb.d/.
#
# Safety Guarantees:
# 1. By PostgreSQL Docker design, /docker-entrypoint-initdb.d/ ONLY executes
#    when $PGDATA is brand new and uninitialized (Mode A).
# 2. It checks ENABLE_BACKUP_RESTORE (default: false). Auto-restore is never
#    triggered unless explicitly enabled by the administrator.
# 3. Mode B Safety: It verifies whether any public tables already exist. If so,
#    it immediately halts restoration without modifying existing tables.
# 4. Marker check: It records and verifies the '_wwtrading_backup_restore_marker'
#    table to prevent duplicate restoration cycles.
# 5. Format validation: It verifies that the dump file begins with 'PGDMP'
#    before invoking pg_restore.
# ==============================================================================

RESTORE_ENABLED="${ENABLE_BACKUP_RESTORE:-false}"
RESTORE_MODE="${BACKUP_RESTORE_MODE:-initial-empty-database}"
BACKUP_DIR="${BACKUP_DIRECTORY:-/backups}"

echo "========================================================================"
echo "[WWTRADING-BACKUP] Docker init hook evaluating DB: '${POSTGRES_DB}'"
echo "  ENABLE_BACKUP_RESTORE : ${RESTORE_ENABLED}"
echo "  BACKUP_RESTORE_MODE   : ${RESTORE_MODE}"
echo "  BACKUP_DIRECTORY      : ${BACKUP_DIR}"
echo "========================================================================"

# Safety Gate 1: Check if auto-restore is enabled
if [ "$RESTORE_ENABLED" != "true" ]; then
    echo "[WWTRADING-BACKUP] Auto-restore is DISABLED (ENABLE_BACKUP_RESTORE != true)."
    echo "[WWTRADING-BACKUP] Initializing standard clean database without backup restoration."
    return 0 2>/dev/null || exit 0
fi

# Safety Gate 2: Mode B Check — Verify if tables already exist in public schema
EXISTING_TABLES=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -t -A -c "
    SELECT count(*) FROM information_schema.tables 
    WHERE table_schema = 'public' 
      AND table_name NOT LIKE 'pg_%';
" 2>/dev/null) || { echo "[WWTRADING-BACKUP] [ERROR] Database safety check failed; aborting."; exit 1; }

if [ "${EXISTING_TABLES:-0}" -gt 0 ]; then
    echo "[WWTRADING-BACKUP] [SAFETY NOTICE - MODE B] Database '${POSTGRES_DB}' already contains ${EXISTING_TABLES} table(s)."
    echo "[WWTRADING-BACKUP] Existing data detected. SKIPPING restore to protect existing data and migrations."
    return 0 2>/dev/null || exit 0
fi

# Safety Gate 3: Check if persistent restoration marker already exists
HAS_MARKER=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -t -A -c "
    SELECT count(*) FROM information_schema.tables 
    WHERE table_schema = 'public' 
      AND table_name = '_wwtrading_backup_restore_marker';
" 2>/dev/null) || { echo "[WWTRADING-BACKUP] [ERROR] Database safety check failed; aborting."; exit 1; }

if [ "${HAS_MARKER:-0}" -gt 0 ]; then
    echo "[WWTRADING-BACKUP] [MARKER DETECTED] Database '${POSTGRES_DB}' was previously restored."
    echo "[WWTRADING-BACKUP] Skipping to prevent duplicate restoration."
    return 0 2>/dev/null || exit 0
fi

# Resolve matching backup file for this database
TARGET_DUMP=""
if [ -n "$BACKUP_FILE" ] && [ -f "$BACKUP_DIR/$BACKUP_FILE" ]; then
    TARGET_DUMP="$BACKUP_DIR/$BACKUP_FILE"
else
    case "$POSTGRES_DB" in
        "keycloak")
            TARGET_DUMP="$BACKUP_DIR/wwtrading-keycloak-db-1.dump"
            ;;
        "wwtrading_users")
            TARGET_DUMP="$BACKUP_DIR/wwtrading-user-db-1.dump"
            ;;
        "wwtrading_orders")
            TARGET_DUMP="$BACKUP_DIR/wwtrading-order-db-1.dump"
            ;;
        "wwtrading_products")
            TARGET_DUMP="$BACKUP_DIR/wwtrading-product-db-1.dump"
            ;;
        "wwtrading_quotations")
            TARGET_DUMP="$BACKUP_DIR/wwtrading-quotation-db-1.dump"
            ;;
        "wwtrading_maintenance")
            TARGET_DUMP="$BACKUP_DIR/wwtrading-maintenance-db-1.dump"
            ;;
        "wwtrading_notifications")
            TARGET_DUMP="$BACKUP_DIR/wwtrading-notification-db-1.dump"
            ;;
        "wwtrading_carts")
            TARGET_DUMP="$BACKUP_DIR/wwtrading-cart-db-1.dump"
            ;;
        "wwtrading_wishlists")
            TARGET_DUMP="$BACKUP_DIR/wwtrading-wishlist-db-1.dump"
            ;;
        "wwtrading_support")
            TARGET_DUMP="$BACKUP_DIR/wwtrading-support-db-1.dump"
            ;;
        *)
            echo "[WWTRADING-BACKUP] [ERROR] No mapped backup file for database '${POSTGRES_DB}'."
            exit 1
            ;;
    esac
fi

# Check file existence
if [ ! -f "$TARGET_DUMP" ]; then
    echo "[WWTRADING-BACKUP] [ERROR] Required backup file '$TARGET_DUMP' not found in $BACKUP_DIR."
    echo "[WWTRADING-BACKUP] Aborting database initialization."
    exit 1
fi

# Validate PostgreSQL Custom format (PGDMP magic header)
DUMP_MAGIC=$(head -c 5 "$TARGET_DUMP" 2>/dev/null || true)
if [ "$DUMP_MAGIC" != "PGDMP" ]; then
    echo "[WWTRADING-BACKUP] [ERROR] Backup file '$TARGET_DUMP' has invalid magic header ('$DUMP_MAGIC' != 'PGDMP')."
    echo "[WWTRADING-BACKUP] Aborting restore to avoid corrupting the database."
    return 1 2>/dev/null || exit 1
fi

DUMP_SIZE=$(wc -c < "$TARGET_DUMP" | tr -d ' ')
echo "[WWTRADING-BACKUP] Starting restoration of $(basename "$TARGET_DUMP") (${DUMP_SIZE} bytes)..."
echo "[WWTRADING-BACKUP] Target DB: '${POSTGRES_DB}' | Target User: '${POSTGRES_USER}'"

# Execute pg_restore into the database
# --no-owner --no-privileges ensures tables belong to $POSTGRES_USER
set +e
pg_restore \
    --exit-on-error \
    --single-transaction \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --no-owner \
    --no-privileges \
    "$TARGET_DUMP"
RESTORE_RC=$?
set -e

if [ $RESTORE_RC -ne 0 ]; then
    echo "[WWTRADING-BACKUP] [ERROR] pg_restore failed with exit code ${RESTORE_RC}."
    return $RESTORE_RC 2>/dev/null || exit $RESTORE_RC
fi

# Record persistent restoration marker table
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
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
    VALUES (
        '$(basename "$TARGET_DUMP")',
        '$POSTGRES_DB',
        '$POSTGRES_USER',
        (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name NOT LIKE 'pg_%' AND table_name != '_wwtrading_backup_restore_marker'),
        'COMPLETED'
    );
EOSQL

TOTAL_RESTORED=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -t -A -c "
    SELECT count(*) FROM information_schema.tables 
    WHERE table_schema = 'public' 
      AND table_name NOT LIKE 'pg_%' 
      AND table_name != '_wwtrading_backup_restore_marker';
")

echo "========================================================================"
echo "[WWTRADING-BACKUP] RESTORATION SUCCESSFUL for '${POSTGRES_DB}'"
echo "  Source file     : $(basename "$TARGET_DUMP")"
echo "  Tables restored : ${TOTAL_RESTORED}"
echo "  Marker created  : _wwtrading_backup_restore_marker"
echo "========================================================================"

