#!/usr/bin/env bash
set -euo pipefail

FAILURE_CLUSTER="${1}"
FAILED_HOST="${2}"
FAILED_PORT="${3}"
NEW_PRIMARY_HOST="${4}"
NEW_PRIMARY_PORT="${5}"

PROXYSQL_HOST="${PROXYSQL_HOST:-proxysql}"
PROXYSQL_ADMIN_PORT="${PROXYSQL_ADMIN_PORT:-6032}"
PROXYSQL_ADMIN_USER="${PROXYSQL_ADMIN_USER:-admin}"
PROXYSQL_ADMIN_PASSWORD="${PROXYSQL_ADMIN_PASSWORD:-admin}"

APP_USER="${APP_USER:-appuser}"
APP_PASSWORD="${APP_PASSWORD:-app1234}"

WRITE_PROXY_PORT="${WRITE_PROXY_PORT:-6401}"
READ_PROXY_PORT="${READ_PROXY_PORT:-6402}"

WRITER_HG=10
READER_HG=20

ALL_MYSQL_HOSTS=("mysql-primary" "mysql-replica1" "mysql-replica2")
HOST_IN_LIST="'mysql-primary','mysql-replica1','mysql-replica2'"

log() {
  echo "[$(date '+%F %T')] $*"
}

mysql_admin() {
  mysql \
    -h"${PROXYSQL_HOST}" \
    -P"${PROXYSQL_ADMIN_PORT}" \
    -u"${PROXYSQL_ADMIN_USER}" \
    -p"${PROXYSQL_ADMIN_PASSWORD}" \
    --batch \
    --skip-column-names \
    "$@"
}

mysql_instance() {
  local host="$1"
  local port="$2"
  shift 2

  mysql \
    -h"${host}" \
    -P"${port}" \
    -u"${APP_USER}" \
    -p"${APP_PASSWORD}" \
    --connect-timeout=2 \
    --batch \
    --skip-column-names \
    "$@"
}

LOCK_FILE="/tmp/proxysql-failover.lock"
exec 9>"${LOCK_FILE}"

flock -n 9 || {
  log "another failover hook is running"
  exit 1
}

log "failover start: cluster=${FAILURE_CLUSTER}, failed=${FAILED_HOST}:${FAILED_PORT}, new_primary=${NEW_PRIMARY_HOST}:${NEW_PRIMARY_PORT}"

# 1. 새 primary가 진짜 writable인지 확인
NEW_PRIMARY_READ_ONLY=$(mysql_instance "${NEW_PRIMARY_HOST}" "${NEW_PRIMARY_PORT}" \
  -e "SELECT @@read_only;" 2>/dev/null || echo "ERROR")

if [[ "${NEW_PRIMARY_READ_ONLY}" != "0" ]]; then
  log "new primary is not writable: host=${NEW_PRIMARY_HOST}, read_only=${NEW_PRIMARY_READ_ONLY}"
  exit 2
fi

log "new primary writable check ok: ${NEW_PRIMARY_HOST}"

# 2. reader 후보 health check
READERS=()

for host in "${ALL_MYSQL_HOSTS[@]}"; do
  if [[ "${host}" == "${FAILED_HOST}" || "${host}" == "${NEW_PRIMARY_HOST}" ]]; then
    continue
  fi

  READ_ONLY=$(mysql_instance "${host}" "3306" \
    -e "SELECT @@read_only;" 2>/dev/null || echo "ERROR")

  if [[ "${READ_ONLY}" == "1" ]]; then
    READERS+=("${host}")
    log "reader candidate ok: ${host}, read_only=${READ_ONLY}"
  else
    log "reader candidate skipped: ${host}, read_only=${READ_ONLY}"
  fi
done

if [[ ${#READERS[@]} -eq 0 ]]; then
  log "warning: no healthy reader candidates. read traffic on HG20 may fail."
fi

# 3. 기존 MySQL 서버들을 먼저 OFFLINE_HARD 처리
mysql_admin <<SQL
UPDATE mysql_servers
SET status = 'OFFLINE_HARD'
WHERE hostname IN (${HOST_IN_LIST})
  AND port = 3306;

LOAD MYSQL SERVERS TO RUNTIME;
SQL

log "all known mysql servers marked OFFLINE_HARD in ProxySQL runtime"

# 4. desired state 재구성
mysql_admin <<SQL
DELETE FROM mysql_servers
WHERE hostname IN (${HOST_IN_LIST})
  AND port = 3306;

INSERT INTO mysql_servers(hostgroup_id, hostname, port, status, max_connections)
VALUES (${WRITER_HG}, '${NEW_PRIMARY_HOST}', ${NEW_PRIMARY_PORT}, 'ONLINE', 200);
SQL

for reader in "${READERS[@]}"; do
  mysql_admin <<SQL
INSERT INTO mysql_servers(hostgroup_id, hostname, port, status, max_connections)
VALUES (${READER_HG}, '${reader}', 3306, 'ONLINE', 200);
SQL
done

mysql_admin <<SQL
LOAD MYSQL SERVERS TO RUNTIME;
SQL

log "ProxySQL runtime mysql_servers loaded"

# 5. query rule은 삭제하지 않고 검증만 함
QUERY_RULE_COUNT=$(mysql_admin <<SQL
SELECT COUNT(*)
FROM runtime_mysql_query_rules
WHERE active = 1
  AND (
    (proxy_port = ${WRITE_PROXY_PORT} AND destination_hostgroup = ${WRITER_HG})
    OR
    (proxy_port = ${READ_PROXY_PORT} AND destination_hostgroup = ${READER_HG})
  );
SQL
)

if [[ "${QUERY_RULE_COUNT}" != "2" ]]; then
  log "ProxySQL query rule verification failed. expected=2, actual=${QUERY_RULE_COUNT}"

  mysql_admin <<SQL
SELECT rule_id, active, proxy_port, destination_hostgroup, apply
FROM runtime_mysql_query_rules
ORDER BY rule_id;
SQL

  exit 3
fi

log "ProxySQL query rule verification ok"

# 6. writer runtime 검증
WRITER_ONLINE_COUNT=$(mysql_admin <<SQL
SELECT COUNT(*)
FROM runtime_mysql_servers
WHERE hostgroup_id = ${WRITER_HG}
  AND hostname = '${NEW_PRIMARY_HOST}'
  AND status = 'ONLINE';
SQL
)

WRONG_WRITER_COUNT=$(mysql_admin <<SQL
SELECT COUNT(*)
FROM runtime_mysql_servers
WHERE hostgroup_id = ${WRITER_HG}
  AND status = 'ONLINE'
  AND hostname <> '${NEW_PRIMARY_HOST}';
SQL
)

if [[ "${WRITER_ONLINE_COUNT}" != "1" || "${WRONG_WRITER_COUNT}" != "0" ]]; then
  log "ProxySQL writer verification failed"

  mysql_admin <<SQL
SELECT hostgroup_id, hostname, port, status
FROM runtime_mysql_servers
WHERE hostname IN (${HOST_IN_LIST})
ORDER BY hostgroup_id, hostname;
SQL

  exit 4
fi

log "ProxySQL writer runtime verification ok"

# 7. reader runtime 검증
for reader in "${READERS[@]}"; do
  READER_ONLINE_COUNT=$(mysql_admin <<SQL
SELECT COUNT(*)
FROM runtime_mysql_servers
WHERE hostgroup_id = ${READER_HG}
  AND hostname = '${reader}'
  AND status = 'ONLINE';
SQL
)

  if [[ "${READER_ONLINE_COUNT}" != "1" ]]; then
    log "ProxySQL reader verification failed: ${reader}"
    exit 5
  fi
done

log "ProxySQL reader runtime verification ok"

# 8. 기존 write frontend session 정리
SESSION_IDS=$(mysql_admin <<SQL
SELECT SessionID
FROM stats_mysql_processlist
WHERE user = '${APP_USER}'
  AND l_srv_port = ${WRITE_PROXY_PORT};
SQL
)

if [[ -n "${SESSION_IDS}" ]]; then
  while read -r sid; do
    [[ -z "${sid}" ]] && continue
    log "kill app write session: ${sid}"
    mysql_admin -e "KILL CONNECTION ${sid};" || true
  done <<< "${SESSION_IDS}"
else
  log "no existing app write sessions to kill"
fi

# 9. 6401 write probe
PROBE_RESULT=$(mysql \
  -h"${PROXYSQL_HOST}" \
  -P"${WRITE_PROXY_PORT}" \
  -u"${APP_USER}" \
  -p"${APP_PASSWORD}" \
  --connect-timeout=2 \
  --batch \
  --skip-column-names \
  -e "
    INSERT INTO failover_probe(id, memo)
    VALUES (1, 'orchestrator-failover')
    ON DUPLICATE KEY UPDATE memo = VALUES(memo);

    SELECT @@hostname, @@read_only, CONNECTION_ID();
  " 2>&1) || {
    log "write probe failed: ${PROBE_RESULT}"
    exit 6
  }

log "write probe ok: ${PROBE_RESULT}"

# 10. 모든 검증 성공 후 디스크 저장
mysql_admin <<SQL
SAVE MYSQL SERVERS TO DISK;
SQL

log "failover complete: writer=${NEW_PRIMARY_HOST}, readers=${READERS[*]:-none}"