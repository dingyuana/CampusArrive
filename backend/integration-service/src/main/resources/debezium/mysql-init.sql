-- ================================================================
-- MW-2.3: MySQL Binlog 配置（Debezium CDC 前置条件）
-- 规格来源: SIM-CA-2026-08 第 6.2 节 CDC 数据同步
-- 参考: https://debezium.io/documentation/reference/2.6/connectors/mysql.html
-- ================================================================

-- 1. 确认 binlog 已启用
-- MySQL 8.0+ 默认启用 binlog
SHOW VARIABLES LIKE 'log_bin';
-- 预期: ON

-- 2. 确认 binlog 格式为 ROW（Debezium 必需）
SHOW VARIABLES LIKE 'binlog_format';
-- 预期: ROW
-- 如不是 ROW，在 my.cnf 中设置:
--   [mysqld]
--   binlog_format=ROW

-- 3. 设置 binlog 行镜像为 FULL（Debezium 需要 before/after 完整镜像）
SET GLOBAL binlog_row_image = FULL;
SHOW VARIABLES LIKE 'binlog_row_image';
-- 预期: FULL

-- 4. 设置 binlog 过期时间（至少 7 天，保证断点续传窗口）
SET GLOBAL binlog_expire_logs_seconds = 604800;
SHOW VARIABLES LIKE 'binlog_expire_logs_seconds';
-- 预期: 604800 (7 天)

-- 5. 确认 GTID 模式（可选，用于主从切换场景的断点续传）
SHOW VARIABLES LIKE 'gtid_mode';
SHOW VARIABLES LIKE 'enforce_gtid_consistency';
-- 如需启用 GTID，在 my.cnf 中设置:
--   [mysqld]
--   gtid_mode=ON
--   enforce_gtid_consistency=ON

-- ================================================================
-- 6. 创建 Debezium 用户并授权
-- ================================================================

-- 创建专用用户（密码生产环境应替换为强密码）
CREATE USER IF NOT EXISTS 'debezium'@'%' IDENTIFIED BY 'dbz_password';

-- 授予 CDC 所需权限
-- SELECT: 读取表结构（初始快照）
-- RELOAD: FLUSH 操作
-- SHOW DATABASES: 列出数据库
-- REPLICATION SLAVE: 读取 binlog
-- REPLICATION CLIENT: SHOW MASTER STATUS
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT
    ON *.* TO 'debezium'@'%';

FLUSH PRIVILEGES;

-- ================================================================
-- 7. 验证配置
-- ================================================================

-- 查看 binlog 文件与位置（Debezium 初始偏移量）
SHOW MASTER STATUS;

-- 查看 binlog 事件（验证 CDC 捕获）
-- SHOW BINLOG EVENTS IN 'mysql-bin.000003' LIMIT 10;
