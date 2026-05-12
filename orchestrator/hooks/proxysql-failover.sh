#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# Orchestrator가 넘겨주는 인자
# 예:
# $1 = local-mysql-cluster
# $2 = mysql-primary
# $3 = 3306
# $4 = mysql-replica1
# $5 = 3306
# =========================================================
FAILURE_CLUSTER="${1}"
FAILED_HOST="${2}"
FAILED_PORT="${3}"
NEW_PRIMARY_HOST="${4}"
NEW_PRIMARY_PORT="${5}"

# =========================================================
# ProxySQL 접속 정보
# =========================================================
PROXYSQL_HOST="${PROXYSQL_HOST:-proxysql}"
PROXYSQL_ADMIN_PORT="${PROXYSQL_ADMIN_PORT:-6032}"
PROXYSQL_ADMIN_USER="${PROXYSQL_ADMIN_USER:-orc_admin}"
PROXYSQL_ADMIN_PASSWORD="${PROXYSQL_ADMIN_PASSWORD:-orc_admin_pass}"

# =========================================================
# 애플리케이션 DB 접속 정보
# 6401 = write port
# 6402 = read port
# =========================================================
APP_USER="${APP_USER:-appuser}"
APP_PASSWORD="${APP_PASSWORD:-app1234}"
APP_DATABASE="${APP_DATABASE:-ecdb}"

WRITE_PROXY_PORT="${WRITE_PROXY_PORT:-6401}"

WRITER_HG=10
READER_HG=20

# 현재 Docker Compose에 존재하는 MySQL 노드 목록
ALL_MYSQL_HOSTS=("mysql-primary" "mysql-replica1" "mysql-replica2")
HOST_IN_LIST="'mysql-primary','mysql-replica1','mysql-replica2'"

log() {
  echo "[$(date '+%F %T')] $*"
}

# ProxySQL Admin 포트로 SQL 실행
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

# MySQL 인스턴스에 직접 접속해서 SQL 실행
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

# =========================================================
# 1. 중복 실행 방지
# 같은 failover hook이 동시에 여러 번 실행되면 ProxySQL 설정이 꼬일 수 있음
# =========================================================
LOCK_FILE="/tmp/proxysql-failover.lock"
exec 9>"${LOCK_FILE}"

flock -n 9 || {
  log "another failover hook is already running"
  exit 1
}

log "failover start: cluster=${FAILURE_CLUSTER}, failed=${FAILED_HOST}:${FAILED_PORT}, new_primary=${NEW_PRIMARY_HOST}:${NEW_PRIMARY_PORT}"

# =========================================================
# 2. 새 primary가 진짜 쓰기 가능한지 확인
# read_only=0이어야 writer로 사용할 수 있음
# =========================================================
NEW_PRIMARY_READ_ONLY=$(mysql_instance "${NEW_PRIMARY_HOST}" "${NEW_PRIMARY_PORT}" \
  -e "SELECT @@read_only;" 2>/dev/null || echo "ERROR")

if [[ "${NEW_PRIMARY_READ_ONLY}" != "0" ]]; then
  log "new primary is not writable: host=${NEW_PRIMARY_HOST}, read_only=${NEW_PRIMARY_READ_ONLY}"
  exit 2
fi

log "new primary writable check ok: ${NEW_PRIMARY_HOST}"

# =========================================================
# 3. reader 후보 계산
# 죽은 서버와 새 primary를 제외한 나머지를 reader 후보로 사용
# =========================================================
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

# =========================================================
# 4. ProxySQL에서 기존 DB 서버들을 먼저 OFFLINE_HARD 처리
# 기존 backend connection / stale routing 영향을 줄이기 위한 단계
# =========================================================
mysql_admin <<SQL
UPDATE mysql_servers
SET status = 'OFFLINE_HARD'
WHERE hostname IN (${HOST_IN_LIST})
  AND port = 3306;

LOAD MYSQL SERVERS TO RUNTIME;
SQL

log "old ProxySQL mysql_servers marked OFFLINE_HARD"

# =========================================================
# 5. ProxySQL mysql_servers를 원하는 최종 상태로 재구성
# HG10 = 새 primary
# HG20 = 남은 replica
# =========================================================
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

log "ProxySQL mysql_servers loaded to runtime"

# =========================================================
# 6. writer hostgroup 검증
# HG10에 ONLINE writer가 새 primary 하나만 있어야 함
# =========================================================
WRITER_COUNT=$(mysql_admin <<SQL
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

if [[ "${WRITER_COUNT}" != "1" || "${WRONG_WRITER_COUNT}" != "0" ]]; then
  log "ProxySQL writer verification failed"

  mysql_admin <<SQL
SELECT hostgroup_id, hostname, port, status
FROM runtime_mysql_servers
WHERE hostname IN (${HOST_IN_LIST})
ORDER BY hostgroup_id, hostname;
SQL

  exit 3
fi

log "ProxySQL writer verification ok"

# =========================================================
# 7. 기존 appuser write frontend session 정리
# Spring/HikariCP가 failover 전 ProxySQL 세션을 재사용하는 문제를 줄임
# =========================================================
SESSION_IDS=$(mysql_admin <<SQL
SELECT SessionID
FROM stats_mysql_processlist
WHERE user = '${APP_USER}'
  AND hostgroup = ${WRITER_HG};
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

# =========================================================
# 8. 실제 write port 6401로 INSERT 테스트
# ProxySQL 설정이 아니라, 앱이 쓰는 경로가 실제로 writable한지 확인
# failover_probe 테이블은 ecdb에 미리 만들어둬야 함
# =========================================================
PROBE_RESULT=$(mysql \
  -h"${PROXYSQL_HOST}" \
  -P"${WRITE_PROXY_PORT}" \
  -u"${APP_USER}" \
  -p"${APP_PASSWORD}" \
  --connect-timeout=2 \
  --batch \
  --skip-column-names \
  "${APP_DATABASE}" \
  -e "
    INSERT INTO failover_probe(id, memo)
    VALUES (1, 'orchestrator-failover')
    ON DUPLICATE KEY UPDATE memo = VALUES(memo);

    SELECT @@hostname, @@read_only, CONNECTION_ID();
  " 2>&1) || {
    log "write probe failed: ${PROBE_RESULT}"
    exit 4
  }

PROBE_READ_ONLY=$(echo "${PROBE_RESULT}" | tail -n 1 | awk '{print $2}')

if [[ "${PROBE_READ_ONLY}" != "0" ]]; then
  log "write probe reached read-only server: ${PROBE_RESULT}"
  exit 5
fi

log "write probe ok: ${PROBE_RESULT}"

# =========================================================
# 9. 모든 검증 성공 후 디스크 저장
# ProxySQL 재시작 후에도 변경된 라우팅 유지
# =========================================================
mysql_admin <<SQL
SAVE MYSQL SERVERS TO DISK;
SQL

log "failover complete: writer=${NEW_PRIMARY_HOST}, readers=${READERS[*]:-none}"