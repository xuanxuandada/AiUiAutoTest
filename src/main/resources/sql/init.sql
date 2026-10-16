-- AI测试平台数据库初始化脚本
-- 数据库: ai_test (已配置在application.yml)

-- ============================================
-- 1. 测试用例分类表
-- ============================================
CREATE TABLE IF NOT EXISTS `test_case_category` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID（数据隔离）',
    `name` VARCHAR(200) NOT NULL COMMENT '分类名称',
    `code` VARCHAR(100) NOT NULL COMMENT '分类编码（唯一标识，如 LOGIN_MODULE）',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID（0表示根分类）',
    `description` TEXT COMMENT '分类描述',
    `sort_order` INT DEFAULT 0 COMMENT '排序号（数字越小越靠前）',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-启用, INACTIVE-禁用',
    `created_by` VARCHAR(100) COMMENT '创建者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    UNIQUE INDEX `uk_tenant_code` (`tenant_id`, `code`),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试用例分类表';

-- ============================================
-- 2. 测试用例表
-- ============================================
CREATE TABLE IF NOT EXISTS `test_case` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用例ID',
    `category_id` BIGINT COMMENT '分类ID（关联 test_case_category）',
    `category_name` VARCHAR(200) COMMENT '分类名称（冗余字段，方便查询）',
    `name` VARCHAR(200) NOT NULL COMMENT '用例名称',
    `origin_description` TEXT COMMENT '用例描述/自然语言描述',
    `target_url` VARCHAR(500) NOT NULL COMMENT '目标测试URL',
    `steps` JSON NOT NULL COMMENT '原始测试步骤（自然语言格式，用户输入的原始数据）',
    `expected_result` TEXT COMMENT '预期结果描述',
    `type` VARCHAR(20) DEFAULT 'MANUAL' COMMENT '用例类型: MANUAL-手动创建, AI_GENERATED-AI生成',
    `tags` VARCHAR(500) COMMENT '标签，逗号分隔',
    `priority` INT DEFAULT 3 COMMENT '优先级: 1-最高, 5-最低',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-启用, INACTIVE-禁用, DEPRECATED-废弃',
    `created_by` VARCHAR(100) COMMENT '创建者',
    `user_id` BIGINT NULL COMMENT '归属用户ID（数据隔离：仅本人及其上级可见）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    INDEX `idx_type` (`type`),
    INDEX `idx_status` (`status`),
    INDEX `idx_priority` (`priority`),
    INDEX `idx_category_id` (`category_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试用例表';

-- ============================================
-- 2.1 测试用例脚本表（AI转换后的Midscene.js可执行步骤）
-- ============================================
CREATE TABLE IF NOT EXISTS `test_case_script` (
    `id` BIGINT PRIMARY KEY COMMENT '脚本ID（与test_case.id一对一）',
    `test_case_id` BIGINT NOT NULL COMMENT '关联用例ID',
    `script_steps` JSON NOT NULL COMMENT 'AI转换后的Midscene.js可执行步骤',
    `model_name` VARCHAR(100) COMMENT '转换使用的AI模型',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '转换状态: PENDING-待转换, CONVERTED-已转换, FAILED-转换失败',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    UNIQUE INDEX `uk_case_id` (`test_case_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试用例脚本表（AI转换后的可执行步骤）';

-- ============================================
-- 2.2 测试用例脚本历史表（编辑前自动备份）
-- ============================================
CREATE TABLE IF NOT EXISTS `test_case_script_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '历史记录ID',
    `script_id` BIGINT NOT NULL COMMENT '原脚本ID',
    `test_case_id` BIGINT NOT NULL COMMENT '关联用例ID',
    `script_steps` JSON NOT NULL COMMENT '备份时的脚本步骤',
    `model_name` VARCHAR(100) COMMENT '转换使用的AI模型',
    `status` VARCHAR(20) COMMENT '备份时的状态',
    `backup_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间',
    INDEX `idx_case_id` (`test_case_id`),
    INDEX `idx_script_id` (`script_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试用例脚本历史表（编辑前自动备份）';

-- ============================================
-- 3. 测试执行记录表
-- ============================================
CREATE TABLE IF NOT EXISTS `test_execution` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '执行ID',
    `test_case_id` BIGINT NOT NULL COMMENT '关联用例ID',
    `suite_id` BIGINT COMMENT '关联回归套件ID(单次执行可为空)',
    `batch_id` VARCHAR(50) COMMENT '批次ID，同一次批量执行的记录共享相同batchId',
    `status` VARCHAR(20) NOT NULL COMMENT '执行状态: PENDING-等待, RUNNING-执行中, PASSED-通过, FAILED-失败, ERROR-异常',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `duration_ms` BIGINT COMMENT '执行耗时(毫秒)',
    `result_detail` JSON COMMENT '执行结果详情(JSON)',
    `error_message` TEXT COMMENT '错误信息',
    `screenshot_url` VARCHAR(500) COMMENT '截图URL',
    `report_url` VARCHAR(500) COMMENT '测试报告URL（OSS地址）',
    `executor` VARCHAR(100) DEFAULT 'SYSTEM' COMMENT '执行者: SYSTEM/MANUAL',
    `user_id` BIGINT NULL COMMENT '归属用户ID（数据隔离：仅本人及其上级可见）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    INDEX `idx_case_id` (`test_case_id`),
    INDEX `idx_suite_id` (`suite_id`),
    INDEX `idx_batch_id` (`batch_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试执行记录表';

-- ============================================
-- 4. 回归测试套件表
-- ============================================
CREATE TABLE IF NOT EXISTS `regression_suite` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '套件ID',
    `name` VARCHAR(200) NOT NULL COMMENT '套件名称',
    `description` TEXT COMMENT '套件描述',
    `login_fetch_command` LONGTEXT COMMENT '登录态提取命令(JSON)',
    `login_enabled` TINYINT DEFAULT 0 COMMENT '是否启用登录态: 1-启用, 0-禁用',
    `schedule_cron` VARCHAR(100) COMMENT '定时执行Cron表达式(可选)',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-启用, INACTIVE-禁用',
    `last_run_time` DATETIME COMMENT '上次执行时间',
    `last_run_result` VARCHAR(20) COMMENT '上次执行结果概要',
    `user_id` BIGINT NULL COMMENT '归属用户ID（数据隔离：仅本人及其上级可见）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    INDEX `idx_status` (`status`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回归测试套件表';

-- ============================================
-- 4.1 套件-用例关联表（多对多中间表）
-- ============================================
CREATE TABLE IF NOT EXISTS `suite_case` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    `suite_id` BIGINT NOT NULL COMMENT '套件ID',
    `case_id` BIGINT NOT NULL COMMENT '用例ID',
    `sort_order` INT DEFAULT 0 COMMENT '排序号（用例在套件中的执行顺序）',
    `steps` JSON NULL COMMENT '套件内副本步骤（NULL=继承父用例，含action字段则无需AI转换）',
    `expected_result` TEXT NULL COMMENT '套件内副本预期结果（NULL=继承父用例）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    UNIQUE INDEX `uk_suite_case` (`suite_id`, `case_id`),
    INDEX `idx_suite_id` (`suite_id`),
    INDEX `idx_case_id` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套件-用例关联表（多对多，含套件内副本）';

-- 兼容已有数据库：为 suite_case 补充副本字段（使用动态SQL兼容所有MySQL版本）
SET @sc_steps_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suite_case' AND COLUMN_NAME = 'steps');
SET @sc_steps_sql := IF(@sc_steps_exists = 0, 'ALTER TABLE suite_case ADD COLUMN steps JSON NULL COMMENT ''套件内副本步骤（NULL=继承父用例）'' AFTER sort_order', 'SELECT 1');
PREPARE sc_steps_stmt FROM @sc_steps_sql;
EXECUTE sc_steps_stmt;
DEALLOCATE PREPARE sc_steps_stmt;

SET @sc_expected_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suite_case' AND COLUMN_NAME = 'expected_result');
SET @sc_expected_sql := IF(@sc_expected_exists = 0, 'ALTER TABLE suite_case ADD COLUMN expected_result TEXT NULL COMMENT ''套件内副本预期结果（NULL=继承父用例）'' AFTER steps', 'SELECT 1');
PREPARE sc_expected_stmt FROM @sc_expected_sql;
EXECUTE sc_expected_stmt;
DEALLOCATE PREPARE sc_expected_stmt;

SET @sc_update_time_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suite_case' AND COLUMN_NAME = 'update_time');
SET @sc_update_time_sql := IF(@sc_update_time_exists = 0, 'ALTER TABLE suite_case ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER create_time', 'SELECT 1');
PREPARE sc_update_time_stmt FROM @sc_update_time_sql;
EXECUTE sc_update_time_stmt;
DEALLOCATE PREPARE sc_update_time_stmt;

-- ============================================
-- 4.2 套件-用例脚本表（套件内副本的AI转换脚本，不覆盖父用例test_case_script）
-- ============================================
CREATE TABLE IF NOT EXISTS `suite_case_script` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '脚本ID',
    `suite_id` BIGINT NOT NULL COMMENT '套件ID',
    `case_id` BIGINT NOT NULL COMMENT '用例ID',
    `script_steps` JSON NOT NULL COMMENT 'AI转换后的Midscene.js可执行步骤',
    `model_name` VARCHAR(100) COMMENT '转换使用的AI模型',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '转换状态: PENDING-待转换, CONVERTED-已转换, FAILED-转换失败',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    UNIQUE INDEX `uk_suite_case` (`suite_id`, `case_id`),
    INDEX `idx_suite_id` (`suite_id`),
    INDEX `idx_case_id` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套件-用例AI转换脚本表（副本，独立于父用例脚本）';

-- ============================================
-- 5. AI对话记录表(用于追溯AI生成用例的过程)
-- ============================================
CREATE TABLE IF NOT EXISTS `ai_generation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `prompt` TEXT NOT NULL COMMENT '用户输入的Prompt',
    `ai_response` TEXT NOT NULL COMMENT 'AI返回内容',
    `generated_case_ids` JSON COMMENT '生成的用例ID列表',
    `model_name` VARCHAR(100) COMMENT '使用的AI模型',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI生成记录表';

-- ============================================
-- 6. 租户表
-- ============================================
CREATE TABLE IF NOT EXISTS `tenant` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '租户ID',
    `name` VARCHAR(200) NOT NULL COMMENT '租户名称（公司/组织名）',
    `code` VARCHAR(50) NOT NULL COMMENT '租户编码（唯一标识）',
    `contact` VARCHAR(100) COMMENT '联系人',
    `phone` VARCHAR(20) COMMENT '联系电话',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-启用, INACTIVE-禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    UNIQUE INDEX `uk_code` (`code`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- ============================================
-- 7. 角色表
-- ============================================
CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    `tenant_id` BIGINT COMMENT '租户ID（为空表示全局角色）',
    `tenant_name` VARCHAR(200) COMMENT '租户名称',
    `name` VARCHAR(100) NOT NULL COMMENT '角色名称',
    `code` VARCHAR(50) NOT NULL COMMENT '角色编码（如 ADMIN, USER, DEPT_LEADER）',
    `description` VARCHAR(500) COMMENT '角色描述',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-启用, INACTIVE-禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    UNIQUE INDEX `uk_tenant_code` (`tenant_id`, `code`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ============================================
-- 8. 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `tenant_id` BIGINT COMMENT '租户ID',
    `role_id` BIGINT COMMENT '角色ID',
    `leader_id` BIGINT NULL COMMENT '直属上级的用户ID（NULL=没有上级；用于数据可见范围）',
    `name` VARCHAR(100) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码',
    `phone`  VARCHAR(20) COMMENT '手机号',
    `age` INT COMMENT '年龄',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` INT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    INDEX `idx_leader_id` (`leader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================
-- 9. 用户AI配置表
--    作用：把大模型 API Key 从配置文件挪到数据库，按登录用户隔离。
--    每个登录用户只能使用「自己」的 Key，互不干扰。
--    注意：本表刻意不做逻辑删除。因为一个用户只能有一条配置，
--    若同时使用唯一索引 user_id + 逻辑删除，删除后再次新增会撞唯一索引。
-- ============================================
CREATE TABLE IF NOT EXISTS `user_ai_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID（一个用户仅一条有效配置）',
    `api_key` VARCHAR(512) COMMENT 'AI API Key（用户自己的密钥）',
    `base_url` VARCHAR(512) COMMENT 'AI接口基址，如 https://dashscope.aliyuncs.com/compatible-mode/v1',
    `model_name` VARCHAR(100) COMMENT '模型名称，如 qwen-vl-max',
    `model_family` VARCHAR(50) COMMENT 'Midscene模型族，如 qwen2.5-vl（浏览器自动化必填）',
    `timeout` INT DEFAULT 60 COMMENT '超时秒数',
    `enabled` INT DEFAULT 1 COMMENT '是否启用: 1-启用, 0-停用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户AI配置表（每个用户使用自己的API Key）';

-- ============================================
-- 10. 兼容已有数据库：补充「数据隔离」相关字段
--     背景：上面的 CREATE TABLE IF NOT EXISTS 只对「新建的库」生效；
--     已经跑起来的老库表已存在，加字段必须用 ALTER，故照抄 suite_case 的动态SQL写法。
--     每段逻辑：先查 information_schema 看列在不在，不在才 ALTER（在就执行 SELECT 1 跳过）。
-- ============================================

-- 10.1 user 表：直属上级
SET @u_leader_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'leader_id');
SET @u_leader_sql := IF(@u_leader_exists = 0, 'ALTER TABLE `user` ADD COLUMN leader_id BIGINT NULL COMMENT ''直属上级的用户ID（NULL=没有上级）'', ADD INDEX idx_leader_id (leader_id)', 'SELECT 1');
PREPARE u_leader_stmt FROM @u_leader_sql;
EXECUTE u_leader_stmt;
DEALLOCATE PREPARE u_leader_stmt;

-- 10.2 test_case 表：归属用户
SET @tc_user_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'test_case' AND COLUMN_NAME = 'user_id');
SET @tc_user_sql := IF(@tc_user_exists = 0, 'ALTER TABLE test_case ADD COLUMN user_id BIGINT NULL COMMENT ''归属用户ID（数据隔离）'', ADD INDEX idx_user_id (user_id)', 'SELECT 1');
PREPARE tc_user_stmt FROM @tc_user_sql;
EXECUTE tc_user_stmt;
DEALLOCATE PREPARE tc_user_stmt;

-- 10.3 regression_suite 表：归属用户
SET @rs_user_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'regression_suite' AND COLUMN_NAME = 'user_id');
SET @rs_user_sql := IF(@rs_user_exists = 0, 'ALTER TABLE regression_suite ADD COLUMN user_id BIGINT NULL COMMENT ''归属用户ID（数据隔离）'', ADD INDEX idx_user_id (user_id)', 'SELECT 1');
PREPARE rs_user_stmt FROM @rs_user_sql;
EXECUTE rs_user_stmt;
DEALLOCATE PREPARE rs_user_stmt;

-- 10.4 test_execution 表：归属用户
SET @te_user_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'test_execution' AND COLUMN_NAME = 'user_id');
SET @te_user_sql := IF(@te_user_exists = 0, 'ALTER TABLE test_execution ADD COLUMN user_id BIGINT NULL COMMENT ''归属用户ID（数据隔离）'', ADD INDEX idx_user_id (user_id)', 'SELECT 1');
PREPARE te_user_stmt FROM @te_user_sql;
EXECUTE te_user_stmt;
DEALLOCATE PREPARE te_user_stmt;

-- ============================================
-- 11. 【老数据回填】—— 只需要做一次，做完可以把这一段重新注释掉
--     为什么要手动回填：老数据的 user_id 是 NULL，而隔离规则是严格的
--     （NULL 不匹配任何可见用户），所以不填的话老数据谁都看不见。
--     步骤：① 先执行下面第 1 条，查出你自己的用户ID；
--          ② 把第 2 组语句里的 1234567890 换成查到的 id；
--          ③ 执行第 2 组三条 UPDATE。
-- ============================================

-- 第 1 步：先查自己的用户ID（把 your_name 换成你的登录用户名）
-- SELECT id, name, tenant_id FROM `user` WHERE name = 'your_name';

-- 第 2 步：把老数据（user_id 为 NULL 的）全部划归到该用户名下
-- UPDATE test_case        SET user_id = 1234567890 WHERE user_id IS NULL;
-- UPDATE regression_suite SET user_id = 1234567890 WHERE user_id IS NULL;
-- UPDATE test_execution   SET user_id = 1234567890 WHERE user_id IS NULL;
