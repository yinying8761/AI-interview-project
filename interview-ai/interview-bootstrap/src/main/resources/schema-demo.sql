















CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(100) NOT NULL,
    `email` VARCHAR(100),
    `phone` VARCHAR(20),
    `nickname` VARCHAR(50),
    `avatar` VARCHAR(255),
    `status` TINYINT DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
    INDEX `idx_username_0` (`username`),
    INDEX `idx_email_1` (`email`)
);


CREATE TABLE IF NOT EXISTS `interview_scenario` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,
    `description` TEXT,
    `tech_field` VARCHAR(50) NOT NULL,
    `difficulty` TINYINT DEFAULT 1,
    `cover_image` VARCHAR(255),
    `question_count` INT DEFAULT 0,
    `sort_order` INT DEFAULT 0,
    `status` TINYINT DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
    INDEX `idx_status_sort_2` (`status`, `sort_order`)
);


CREATE TABLE IF NOT EXISTS `interview_question` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `scenario_id` BIGINT NOT NULL,
    `content` TEXT NOT NULL,
    `type` VARCHAR(20) DEFAULT 'technical',
    `difficulty` TINYINT DEFAULT 1,
    `expected_answer` TEXT,
    `keywords` VARCHAR(500),
    `sort_order` INT DEFAULT 0,
    `status` TINYINT DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
    INDEX `idx_scenario_3` (`scenario_id`),
    INDEX `idx_status_4` (`status`)
);


CREATE TABLE IF NOT EXISTS `interview_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `scenario_id` BIGINT NOT NULL,
    `paper_id` BIGINT DEFAULT NULL,
    `status` VARCHAR(20) DEFAULT 'pending',
    `score` DECIMAL(5,2),
    `duration` INT,
    `answer_data` TEXT,
    `ai_session_id` VARCHAR(64),
    `current_phase` VARCHAR(20) DEFAULT 'ASKING',
    `report_json` LONGTEXT,
    `submit_id` VARCHAR(64),
    `ai_feedback` TEXT,
    `started_at` DATETIME,
    `completed_at` DATETIME,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
    INDEX `idx_user_5` (`user_id`),
    INDEX `idx_scenario_6` (`scenario_id`),
    INDEX `idx_created_7` (`created_at`)
);


CREATE TABLE IF NOT EXISTS `user_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    UNIQUE KEY `uk_user_role_8` (`user_id`, `role_id`)
);


CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL,
    `code` VARCHAR(50) NOT NULL UNIQUE,
    `description` VARCHAR(255),
    `status` TINYINT DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE IF NOT EXISTS `permission` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL,
    `code` VARCHAR(100) NOT NULL UNIQUE,
    `type` VARCHAR(20) DEFAULT 'api',
    `sort_order` INT DEFAULT 0,
    `status` TINYINT DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE IF NOT EXISTS `role_permission` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `role_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    UNIQUE KEY `uk_role_perm_9` (`role_id`, `permission_id`)
);


INSERT INTO `sys_user` (`username`, `password`, `email`, `nickname`) VALUES
('admin',  '123456', 'admin@example.com',  '管理员'),
('test01', '123456', 'test01@example.com', '测试用户1');

INSERT INTO `interview_scenario`
    (`name`, `description`, `tech_field`, `difficulty`, `question_count`)
VALUES
('Java后端工程师', '考察Java基础、Spring框架、数据库等', '后端', 2, 5),
('前端工程师', '考察HTML/CSS、JavaScript、Vue/React等', '前端', 2, 5),
('测试工程师', '考察测试理论、自动化测试、性能测试等', '测试', 1, 5),
('产品经理', '考察需求分析、产品规划、指标设计和项目推进能力', '产品', 2, 5);

INSERT INTO `interview_question`
    (`scenario_id`, `content`, `type`, `difficulty`, `expected_answer`, `keywords`, `sort_order`)
VALUES
(1, '请介绍一下Java中的集合框架，ArrayList和LinkedList的区别是什么？', 'technical', 1,
 'ArrayList基于数组实现，随机访问快；LinkedList基于链表实现，插入删除快。',
 'Java集合,ArrayList,LinkedList,数组,链表', 1),
(1, 'Spring Boot的自动配置原理是什么？', 'technical', 2,
 '通过@EnableAutoConfiguration注解，加载META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports文件中的自动配置类。',
 'Spring Boot,自动配置,EnableAutoConfiguration,条件装配', 2),
(1, 'JVM 的主要运行时内存区域有哪些？', 'technical', 2,
 '主要包括程序计数器、虚拟机栈、本地方法栈、堆和方法区；程序计数器、虚拟机栈和本地方法栈线程私有，堆和方法区线程共享。',
 'JVM,程序计数器,虚拟机栈,本地方法栈,堆,方法区', 3),
(1, 'MySQL 索引为什么通常使用 B+ 树而不是二叉树或哈希表？', 'technical', 2,
 'B+树是多路平衡树，树高较低，可以减少磁盘IO，叶子节点有序链表便于范围查询；哈希索引只适合等值查询。',
 'MySQL,索引,B+树,磁盘IO,范围查询,哈希索引', 4),
(1, 'Spring 声明式事务在哪些情况下会失效？', 'technical', 3,
 '常见于同类方法自调用、方法不是 public、异常被捕获、抛出异常类型不匹配、目标类未被 Spring 代理等场景。',
 'Spring,事务,自调用,代理,AOP,异常回滚', 5),
(2, 'Vue 3 的响应式系统是如何工作的？', 'technical', 2,
 'Vue 3 使用 Proxy 拦截对象读写，通过 track 收集依赖、trigger 触发更新，并配合 effect 调度组件渲染。',
 'Vue 3,Proxy,响应式,track,trigger,effect', 1),
(2, '从输入 URL 到页面展示，浏览器主要经历了哪些过程？', 'technical', 2,
 '主要包括 DNS 解析、建立 TCP 和 TLS 连接、发送 HTTP 请求、接收响应、解析 HTML/CSS/JavaScript、构建渲染树并完成布局和绘制。',
 'URL,DNS,TCP,TLS,HTTP,渲染,布局,绘制', 2),
(2, '什么是闭包？它为什么可能导致内存泄漏？', 'technical', 2,
 '闭包是函数与其词法作用域的组合；闭包会持续引用外层变量，如果长期持有不再需要的大对象或 DOM 引用，就可能导致内存无法释放。',
 '闭包,词法作用域,内存泄漏,DOM,垃圾回收', 3),
(2, '前端性能优化可以从哪些方面入手？', 'technical', 2,
 '可以从资源体积、网络加载、渲染性能、缓存策略和运行时性能入手，例如代码分割、懒加载、CDN、图片优化、减少重排重绘和长任务。',
 '性能优化,代码分割,懒加载,CDN,缓存,重排重绘', 4),
(2, '浏览器跨域问题的原因和常见解决方案是什么？', 'technical', 2,
 '浏览器基于同源策略限制不同协议、域名或端口之间的请求；常见方案包括服务端配置 CORS、开发环境代理、网关转发，以及满足条件时使用 JSONP。',
 '跨域,同源策略,CORS,代理,JSONP,浏览器', 5),
(3, '设计测试用例时常用的方法有哪些？', 'technical', 1,
 '常用方法包括等价类划分、边界值分析、判定表、因果图、状态迁移、场景法和错误推测法等。',
 '测试用例,等价类,边界值,判定表,状态迁移,场景法', 1),
(3, '一个完整的缺陷生命周期通常包含哪些状态？', 'technical', 1,
 '通常包括新建、已分配、处理中、已修复、待验证、已关闭，以及验证失败后的重新打开等状态。',
 '缺陷生命周期,新建,已分配,已修复,待验证,关闭,重新打开', 2),
(3, '接口测试主要需要关注哪些方面？', 'technical', 2,
 '需要关注请求参数、响应状态码和数据结构、业务逻辑、鉴权、异常与边界、幂等性、兼容性、性能和安全性。',
 '接口测试,请求参数,响应码,数据结构,鉴权,幂等性,边界', 3),
(3, '自动化测试金字塔是什么？各层应该如何投入？', 'technical', 2,
 '测试金字塔由大量单元测试、适量接口或服务测试、少量端到端 UI 测试组成；越底层的测试执行越快、稳定性越高，应投入更多。',
 '自动化测试,测试金字塔,单元测试,接口测试,UI测试,稳定性', 4),
(3, '页面出现问题时，如何快速判断是前端还是后端问题？', 'technical', 2,
 '先稳定复现并查看浏览器 Network、Console 和请求响应，确认请求参数与状态码；再结合服务端日志、接口直连和数据库数据逐层定位。',
 '问题定位,Network,Console,请求响应,服务端日志,复现', 5),
(4, '产品经理如何对需求进行优先级排序？', 'technical', 2,
 '可以结合业务价值、用户价值、实现成本、风险、紧急程度和战略目标，使用 RICE、Kano、MoSCoW 等方法进行综合评估。',
 '需求优先级,RICE,Kano,MoSCoW,业务价值,实现成本,战略目标', 1),
(4, '一份完整的 PRD 通常应该包含哪些内容？', 'technical', 2,
 '通常包含需求背景、目标、用户与场景、功能范围、业务流程、详细需求、验收标准、数据指标、依赖风险和版本计划。',
 'PRD,需求背景,用户场景,功能范围,验收标准,数据指标,版本计划', 2),
(4, '如何验证一个需求是否值得投入开发？', 'technical', 2,
 '可以通过用户访谈、数据分析、竞品研究、可用性测试和 MVP 验证需求和价值假设，并评估投入产出比、风险与战略匹配度。',
 '需求验证,用户访谈,数据分析,竞品分析,MVP,投入产出比', 3),
(4, '什么是北极星指标？选择时要注意什么？', 'technical', 3,
 '北极星指标是能够反映产品核心用户价值、指引团队长期方向的单一关键指标；选择时要保证可衡量、可影响、与业务目标一致，并避免虚荣指标。',
 '北极星指标,用户价值,可衡量,业务目标,虚荣指标,长期方向', 4),
(4, '面对需求变更时，产品经理应该如何推进？', 'behavioral', 2,
 '先理解变更原因和目标，评估用户价值、研发成本、进度与风险，再与业务和研发共同评审优先级，明确版本安排并同步所有相关方。',
 '需求变更,影响评估,优先级,评审,沟通协作,版本管理', 5);


UPDATE `interview_scenario` s
SET s.`question_count` = (
    SELECT COUNT(*)
    FROM `interview_question` q
    WHERE q.`scenario_id` = s.`id`
      AND q.`status` = 1
);


INSERT INTO `role` (`name`, `code`, `description`) VALUES
('管理员', 'admin', '管理用户、题库和全部数据，不参与面试'),
('面试官', 'interviewer', '查看记录、评分'),
('候选人', 'candidate', '参加面试、查看自己的记录和报告');


INSERT INTO `permission` (`name`, `code`, `type`) VALUES
('查看用户',       'user:view',         'api'),
('修改用户',       'user:update',       'api'),
('查看题目',       'question:view',     'api'),
('新增题目',       'question:create',   'api'),
('修改题目',       'question:update',   'api'),
('删除题目',       'question:delete',   'api'),
('查看场景',       'scenario:view',     'api'),
('新增场景',       'scenario:create',   'api'),
('修改场景',       'scenario:update',   'api'),
('删除场景',       'scenario:delete',   'api'),
('创建面试记录',   'record:create',     'api'),
('查看面试记录',   'record:view',       'api'),
('面试评分',       'record:score',      'api'),
('开始面试',       'interview:start',   'api'),
('提交面试',       'interview:submit',  'api'),
('取消面试',       'interview:cancel',  'api'),
('查看报告',       'report:view',       'api');



INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p
WHERE r.`code` = 'admin'
  AND p.`code` IN (
      'user:view', 'user:update',
      'question:view', 'question:create', 'question:update', 'question:delete',
      'scenario:view', 'scenario:create', 'scenario:update', 'scenario:delete',
      'record:view', 'record:score', 'report:view'
  );


INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p
WHERE r.`code` = 'interviewer'
  AND p.`code` IN (
      'record:view', 'record:score',
      'question:view', 'scenario:view', 'report:view'
  );


INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p
WHERE r.`code` = 'candidate'
  AND p.`code` IN (
      'scenario:view',
      'record:create', 'record:view',
      'interview:start', 'interview:submit', 'interview:cancel',
      'report:view'
  );


INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `sys_user` u
JOIN `role` r ON r.`code` = 'admin'
WHERE u.`username` = 'admin';

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `sys_user` u
JOIN `role` r ON r.`code` = 'candidate'
WHERE u.`username` = 'test01';



CREATE TABLE IF NOT EXISTS `resume` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `current_version` INT NOT NULL DEFAULT 1,
  `parsed_name` VARCHAR(64),
  `parsed_phone` VARCHAR(32),
  `parsed_email` VARCHAR(128),
  `education_summary` TEXT,
  `skill_summary` TEXT,
  `project_summary` TEXT,
  `status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
  KEY `idx_user_id_10` (`user_id`)
);

CREATE TABLE IF NOT EXISTS `resume_version` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `resume_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `version` INT NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `object_name` VARCHAR(512) NOT NULL,
  `file_size` BIGINT,
  `content_type` VARCHAR(128),
  `file_type` VARCHAR(32),
  `status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_resume_version_11` (`resume_id`, `version`),
  KEY `idx_user_id_12` (`user_id`)
);

CREATE TABLE IF NOT EXISTS `resume_keyword` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `resume_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `keyword` VARCHAR(128) NOT NULL,
  `type` VARCHAR(32) NOT NULL DEFAULT 'SKILL',
  `weight` DECIMAL(5,2) DEFAULT 1.00,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_resume_id_13` (`resume_id`),
  KEY `idx_user_id_14` (`user_id`)
);



CREATE TABLE IF NOT EXISTS `question_tag` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(64) NOT NULL,
  `code` VARCHAR(64) NOT NULL,
  `type` VARCHAR(32) NOT NULL,
  `parent_id` BIGINT DEFAULT NULL,
  `sort_order` INT DEFAULT 0,
  `status` TINYINT DEFAULT 1,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
  UNIQUE KEY `uk_tag_code_15` (`code`),
  KEY `idx_tag_type_status_16` (`type`, `status`)
);


CREATE TABLE IF NOT EXISTS `question_tag_rel` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `question_id` BIGINT NOT NULL,
  `tag_id` BIGINT NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_question_tag_17` (`question_id`, `tag_id`),
  KEY `idx_tag_question_18` (`tag_id`, `question_id`)
);


CREATE TABLE IF NOT EXISTS `question_paper` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `scenario_id` BIGINT NOT NULL,
  `title` VARCHAR(128) NOT NULL,
  `round` VARCHAR(32) DEFAULT 'TECH_ROUND_1',
  `total_count` INT NOT NULL,
  `strategy_json` LONGTEXT,
  `status` VARCHAR(32) DEFAULT 'GENERATED',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
  KEY `idx_paper_scenario_19` (`scenario_id`, `status`),
  KEY `idx_paper_user_20` (`user_id`)
);


CREATE TABLE IF NOT EXISTS `question_paper_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `paper_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 1,
  `score` INT NOT NULL DEFAULT 10,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_paper_question_21` (`paper_id`, `question_id`),
  KEY `idx_paper_sort_22` (`paper_id`, `sort_order`)
);


CREATE TABLE IF NOT EXISTS `question_stat` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `question_id` BIGINT NOT NULL,
  `use_count` INT NOT NULL DEFAULT 0,
  `last_used_at` DATETIME DEFAULT NULL,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
  UNIQUE KEY `uk_question_stat_23` (`question_id`)
);


ALTER TABLE `interview_question` ADD INDEX `idx_scenario_status_difficulty_24` (`scenario_id`, `status`, `difficulty`);

ALTER TABLE `question_tag` ADD INDEX `idx_type_code_25` (`type`, `code`);



INSERT INTO `question_tag` (`name`, `code`, `type`, `sort_order`) VALUES
('Spring Boot', 'SPRING_BOOT',  'KNOWLEDGE', 1),
('MySQL',       'MYSQL',       'KNOWLEDGE', 2),
('Redis',       'REDIS',       'KNOWLEDGE', 3),
('JVM',         'JVM',         'KNOWLEDGE', 4),
('Java 核心',   'JAVA_CORE',   'KNOWLEDGE', 5),
('Java 后端',   'JAVA_BACKEND','JOB',       1),
('前端开发',    'FRONTEND',    'JOB',       2),
('测试',        'TEST',        'JOB',       3),
('产品',        'PRODUCT',     'JOB',       4),
('技术一面',    'TECH_ROUND_1','ROUND',     1),
('技术二面',    'TECH_ROUND_2','ROUND',     2),
('HR 面',       'HR_ROUND',    'ROUND',     3),
('技术题',      'TECHNICAL',   'TYPE',      1),
('行为题',      'BEHAVIORAL',  'TYPE',      2),
('HR 题',       'HR',          'TYPE',      3);


INSERT INTO `interview_question`
    (`scenario_id`, `content`, `type`, `difficulty`, `expected_answer`, `keywords`, `sort_order`)
VALUES
(1, 'Redis 缓存常见的穿透、击穿、雪崩问题分别是什么？如何应对？', 'technical', 2,
 '缓存穿透指查询不存在的数据大量打到数据库，可用布隆过滤或空值缓存；缓存击穿指热点 key 过期并发打到数据库，可用互斥锁或热点不设；缓存雪崩指大量 key 同时过期，可用过期时间随机化、多级缓存和限流。',
 'Redis,缓存穿透,缓存击穿,缓存雪崩,布隆过滤器,互斥锁', 6),
(1, 'Spring 出现循环依赖时是如何解决的？', 'technical', 3,
 'Spring 通过三级缓存（早期引用、单例池、对象工厂）在构造注入之外的场景提前暴露 Bean，解决单例 Bean 的属性循环依赖；构造器循环依赖无法通过缓存解决，需要重构或用 @Lazy。',
 'Spring,循环依赖,三级缓存,Bean,依赖注入,懒加载', 7),
(1, 'MySQL 索引在哪些情况下会失效？', 'technical', 2,
 '常见包括对索引列做函数或运算、隐式类型转换、最左前缀原则不满足、OR 连接非索引列、like 以 % 开头、使用不等于或 is null 等。',
 'MySQL,索引失效,最左前缀,隐式转换,函数,like', 8),
(1, 'JVM 常用的垃圾回收算法有哪些？各有什么特点？', 'technical', 3,
 '主要包括标记-清除、标记-复制和标记-整理；标记-清除会产生碎片，标记-复制适合新生代，标记-整理适合老年代，同时可结合分代收集。',
 'JVM,垃圾回收,标记清除,标记复制,标记整理,分代', 9),
(1, '如何基于 Redis 实现一个可靠的分布式锁？', 'technical', 3,
 '常用 SET NX PX 加锁并携带唯一值，释放时用 Lua 脚本校验值后删除，防止误删；可结合续期或 Redisson 避免锁过期导致的并发问题。',
 'Redis,分布式锁,SETNX,过期时间,Lua,Redisson', 10),
(1, 'Java 线程池的核心参数有哪些？为什么要合理设置？', 'technical', 2,
 '核心参数包括核心线程数、最大线程数、空闲存活时间、工作队列和拒绝策略；合理设置能平衡并发能力与资源占用，避免无界队列内存溢出或频繁创建线程。',
 'Java,线程池,核心线程数,工作队列,拒绝策略,TaskExecutor', 1),
(1, 'Spring Boot 如何整合 Redis 并配置序列化？', 'technical', 2,
 '通过 spring-boot-starter-data-redis 引入依赖，在配置类中定义 RedisTemplate 并设置 Key 字符串序列化、Value LONGTEXT 序列化，同时注册 JavaTimeModule 支持日期类型。',
 'Spring Boot,Redis,序列化,RedisTemplate,StringRedisSerializer', 2),
(1, 'MySQL 的事务隔离级别有哪些？各自解决什么问题？', 'technical', 2,
 '包括读未提交、读已提交、可重复读和串行化；读已提交和可重复读分别解决脏读与不可重复读，可重复读是 MySQL 默认级别，串行化通过锁解决幻读。',
 'MySQL,事务隔离级别,脏读,不可重复读,幻读,可重复读', 3),
(1, '请描述你项目中遇到的最大技术难点及解决过程。', 'behavioral', 2,
 '回答应体现真实场景、解决思路、取舍与复盘，结构清晰，能体现技术判断与协作能力。',
 '技术难点,问题定位,解决方案,复盘,协作', 4),
(1, '当团队对技术方案产生分歧时，你会如何处理？', 'hr', 1,
 '先明确目标和约束，共同评估方案价值、风险与成本，必要时用数据或原型验证，最终达成一致并记录决策依据。',
 '团队协作,方案分歧,沟通,决策,评估', 5);


INSERT INTO `question_tag_rel` (`question_id`, `tag_id`) VALUES

(1, 5), (1, 6), (1, 10), (1, 13),
(2, 1), (2, 6), (2, 10), (2, 13),
(3, 4), (3, 6), (3, 10), (3, 13),
(4, 2), (4, 6), (4, 10), (4, 13),
(5, 1), (5, 6), (5, 11), (5, 13),

(21, 3), (21, 6), (21, 10), (21, 13),
(22, 1), (22, 6), (22, 11), (22, 13),
(23, 2), (23, 6), (23, 10), (23, 13),
(24, 4), (24, 6), (24, 11), (24, 13),
(25, 3), (25, 6), (25, 11), (25, 13),
(26, 5), (26, 6), (26, 10), (26, 13),
(27, 1), (27, 3), (27, 6), (27, 10), (27, 13),
(28, 2), (28, 6), (28, 10), (28, 13),
(29, 6), (29, 11), (29, 14),
(30, 6), (30, 12), (30, 15),

(6, 7), (6, 10), (6, 13),
(7, 7), (7, 10), (7, 13),
(8, 7), (8, 10), (8, 13),
(9, 7), (9, 10), (9, 13),
(10, 7), (10, 10), (10, 13),

(11, 8), (11, 10), (11, 13),
(12, 8), (12, 10), (12, 13),
(13, 8), (13, 10), (13, 13),
(14, 8), (14, 10), (14, 13),
(15, 8), (15, 10), (15, 13),

(16, 9), (16, 10), (16, 14),
(17, 9), (17, 10), (17, 14),
(18, 9), (18, 10), (18, 14),
(19, 9), (19, 10), (19, 14),
(20, 9), (20, 10), (20, 14);


UPDATE `interview_scenario` s
SET s.`question_count` = (
    SELECT COUNT(*)
    FROM `interview_question` q
    WHERE q.`scenario_id` = s.`id`
      AND q.`status` = 1
);


INSERT IGNORE INTO `question_tag` (`name`, `code`, `type`, `sort_order`) VALUES
('Docker',       'DOCKER',       'KNOWLEDGE', 6),
('Kubernetes',   'KUBERNETES',   'KNOWLEDGE', 7),
('消息队列',     'MQ',           'KNOWLEDGE', 8),
('MyBatis',      'MYBATIS',      'KNOWLEDGE', 9),
('Spring MVC',   'SPRING_MVC',   'KNOWLEDGE', 10),
('并发编程',     'CONCURRENCY',  'KNOWLEDGE', 11),
('分布式',       'DISTRIBUTED',  'KNOWLEDGE', 12),
('网络协议',     'NETWORK',      'KNOWLEDGE', 13),
('Git 与协作',   'GIT',          'KNOWLEDGE', 14),
('算法与数据结构','ALGORITHM',    'KNOWLEDGE', 15),
('DevOps',       'DEVOPS',       'JOB',       5),
('云原生',       'CLOUD',        'JOB',       6);


INSERT INTO `interview_question`
    (`scenario_id`, `content`, `type`, `difficulty`, `expected_answer`, `keywords`, `sort_order`)
VALUES
(1, 'HTTP 与 HTTPS 的主要区别是什么？HTTPS 如何保证安全？', 'technical', 1,
 'HTTPS 在 HTTP 基础上通过 TLS/SSL 加密传输，保证机密性、完整性与身份认证；通过对称加密传输数据、非对称加密协商密钥，并依赖证书校验服务器身份。',
 'HTTP,HTTPS,TLS,SSL,加密,证书,对称加密,非对称加密', 11),
(1, '设计 RESTful API 时有哪些基本原则？', 'technical', 1,
 '使用资源名词表示资源、利用 HTTP 方法表达语义（GET/POST/PUT/DELETE）、使用状态码表达结果、无状态、统一资源路径、提供合理的幂等性与版本管理。',
 'RESTful,HTTP方法,状态码,无状态,资源,幂等,版本管理', 12),
(1, 'equals 与 hashCode 为什么需要同时重写？', 'technical', 1,
 'equals 规定逻辑相等，hashCode 用于散列集合定位；两对象 equals 相等则 hashCode 必须相等，否则在 HashMap/HashSet 中会出现无法正确查找或重复存储的问题。',
 'equals,hashCode,HashMap,HashSet,散列,重写', 13),
(1, '进程和线程有哪些区别？', 'technical', 1,
 '进程是资源分配的基本单位，拥有独立地址空间；线程是 CPU 调度的基本单位，同一进程内线程共享内存与资源。线程更轻量，但共享状态需要同步控制。',
 '进程,线程,资源分配,CPU调度,地址空间,上下文切换', 14),
(1, 'Git 常用的版本管理命令有哪些？各自用途是什么？', 'technical', 1,
 '常用包括 git init/clone/add/commit/push/pull/branch/merge/rebase/log/reset/revert；分别用于初始化、克隆、暂存、提交、推送、拉取、分支、合并、变基、查看历史、回退和撤销。',
 'Git,版本管理,commit,push,pull,branch,merge,rebase,reset', 15),
(1, 'MyBatis 与 JPA/Hibernate 有什么区别？如何选择？', 'technical', 2,
 'MyBatis 以 SQL 为中心、半自动化，便于控制 SQL 和优化；JPA/Hibernate 以对象为中心、自动生成 SQL，开发效率高但复杂查询难控。按团队对 SQL 掌控度和业务复杂度选择。',
 'MyBatis,JPA,Hibernate,ORM,SQL,映射', 6),
(1, '一次 Spring MVC 请求从进入到返回经历哪些步骤？', 'technical', 2,
 '请求经 DispatcherServlet，由 HandlerMapping 定位处理器，经 HandlerAdapter 调用 Controller，返回 ModelAndView，再经 ViewResolver 解析视图并渲染，最后响应客户端。',
 'Spring MVC,DispatcherServlet,HandlerMapping,HandlerAdapter,Controller,ViewResolver', 7),
(1, 'Docker 镜像和容器有什么区别？', 'technical', 2,
 '镜像是只读的构建模板，包含运行环境与依赖；容器是镜像运行时的实例，采用分层文件系统与命名空间隔离，可读可写，生命周期短。',
 'Docker,镜像,容器,分层,文件系统,隔离', 8),
(1, '消息队列在系统中的作用有哪些？如何选择消息中间件？', 'technical', 2,
 '作用包括异步解耦、削峰填谷、流量缓冲、事件驱动和大数据管道；选择需考虑吞吐量、可靠性、顺序性、延迟、生态与运维成本，例如 Kafka、RocketMQ、RabbitMQ。',
 '消息队列,异步解耦,削峰,Kafka,RocketMQ,RabbitMQ,可靠性', 9),
(1, '分布式系统 CAP 理论是什么？如何取舍？', 'technical', 2,
 'CAP 指一致性、可用性、分区容错性三者不可同时完全满足；分布式系统必须支持分区容错，在分区时在一致性与可用性之间权衡，例如 CP 与 AP 策略。',
 'CAP,一致性,可用性,分区容错,分布式,CP,AP', 10),
(1, 'Kubernetes 中 Pod、Deployment、Service 分别起什么作用？', 'technical', 3,
 'Pod 是最小调度单元，封装一个或多个容器；Deployment 管理副本与滚动更新；Service 提供稳定的访问入口与服务发现。',
 'Kubernetes,K8s,Pod,Deployment,Service,滚动更新,服务发现', 11),
(1, 'MySQL 主从复制和读写分离的实现原理是什么？', 'technical', 3,
 '主库将变更写入 binlog，从库拉取 binlog 并重放到自身，实现数据同步；读写分离将读请求路由到从库、写请求到主库，以分摊读压力，需处理主从延迟与一致性。',
 'MySQL,主从复制,binlog,读写分离,主从延迟,一致性', 12),
(1, '高并发场景下如何设计幂等？', 'technical', 3,
 '通过唯一业务键、数据库唯一约束、悲观/乐观锁、分布式锁、状态机或幂等表等方式，保证同一请求重复执行结果一致。',
 '幂等,唯一键,唯一约束,乐观锁,分布式锁,状态机', 13),
(1, 'Kafka 如何保证消息不丢失和有序？', 'technical', 3,
 '通过 acks 配置、生产者重试、副本机制与 ISR 保证可靠性；有序性依赖分区内有序与 key 路由，跨分区需业务侧聚合或单分区。',
 'Kafka,消息不丢失,acks,副本,ISR,分区,有序', 14),
(1, 'JVM 出现 CPU 飙高或内存溢出时如何排查？', 'technical', 3,
 '可用 jps/jstack/jstat/jmap/jconsole/arthas 等工具定位：CPU 飙高看线程栈热点，内存溢出分析堆与 GC 日志、抓取 heap dump 分析对象引用与泄漏。',
 'JVM,CPU飙高,内存溢出,jstack,jmap,GC日志,heap dump,Arthas', 15);


INSERT IGNORE INTO `question_tag_rel` (`question_id`, `tag_id`) VALUES
(31, 23), (31, 6), (31, 10), (31, 13),
(32, 20), (32, 23), (32, 6), (32, 10), (32, 13),
(33, 5), (33, 6), (33, 10), (33, 13),
(34, 5), (34, 21), (34, 6), (34, 10), (34, 13),
(35, 24), (35, 6), (35, 10), (35, 13),
(36, 19), (36, 6), (36, 10), (36, 13),
(37, 20), (37, 6), (37, 10), (37, 13),
(38, 16), (38, 6), (38, 10), (38, 13),
(39, 18), (39, 6), (39, 10), (39, 13),
(40, 22), (40, 6), (40, 11), (40, 13),
(41, 17), (41, 6), (41, 11), (41, 13),
(42, 2), (42, 6), (42, 11), (42, 13),
(43, 21), (43, 22), (43, 6), (43, 11), (43, 13),
(44, 18), (44, 6), (44, 11), (44, 13),
(45, 4), (45, 6), (45, 11), (45, 13);


UPDATE `interview_scenario` s
SET s.`question_count` = (
    SELECT COUNT(*)
    FROM `interview_question` q
    WHERE q.`scenario_id` = s.`id`
      AND q.`status` = 1
);






ALTER TABLE `interview_record` ADD COLUMN `interviewer_id` BIGINT DEFAULT NULL;
ALTER TABLE `interview_record` ADD COLUMN `room_code` VARCHAR(64) DEFAULT NULL;
ALTER TABLE `interview_record` ADD COLUMN `scheduled_at` DATETIME DEFAULT NULL;
ALTER TABLE `interview_record` ADD COLUMN `planned_end_at` DATETIME DEFAULT NULL;
ALTER TABLE `interview_record` ADD COLUMN `reminder_sent` TINYINT DEFAULT 0;
ALTER TABLE `interview_record` ADD COLUMN `question_index` INT DEFAULT 0;
ALTER TABLE `interview_record` ADD COLUMN `metadata_json` LONGTEXT DEFAULT NULL;
ALTER TABLE `interview_record` ADD UNIQUE KEY `uk_record_room_26` (`room_code`);
ALTER TABLE `interview_record` ADD KEY `idx_record_user_status_time_27` (`user_id`, `status`, `scheduled_at`);
ALTER TABLE `interview_record` ADD KEY `idx_record_scheduled_reminder_28` (`status`, `scheduled_at`, `reminder_sent`);


CREATE TABLE IF NOT EXISTS `interview_session_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `operator_id` BIGINT DEFAULT NULL,
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `payload_json` LONGTEXT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_event_record_time_29` (`record_id`, `event_time`),
  KEY `idx_event_type_30` (`event_type`)
);


INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r
JOIN `permission` p
WHERE r.`code` = 'interviewer'
  AND p.`code` IN ('interview:start', 'interview:submit', 'interview:cancel');


INSERT INTO `sys_user` (`username`, `password`, `email`, `nickname`)
SELECT 'host01', '123456', 'host01@example.com', '面试官1'
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username` = 'host01');

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `sys_user` u
JOIN `role` r ON r.`code` = 'interviewer'
WHERE u.`username` = 'host01';





CREATE TABLE IF NOT EXISTS `interview_audio_segment` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `question_index` INT NOT NULL DEFAULT 1,
  `segment_seq` INT NOT NULL DEFAULT 1,
  `start_at` DATETIME NOT NULL,
  `end_at` DATETIME DEFAULT NULL,
  `duration_ms` INT DEFAULT 0,
  `file_url` VARCHAR(512) DEFAULT NULL,
  `mime_type` VARCHAR(64) DEFAULT NULL,
  `file_size` BIGINT DEFAULT 0,
  `sample_rate` INT DEFAULT NULL,
  `channels` INT DEFAULT 1,
  `avg_volume` DECIMAL(6,4) DEFAULT NULL,
  `max_volume` DECIMAL(6,4) DEFAULT NULL,
  `silence_ratio` DECIMAL(6,4) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'RECORDING',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
  UNIQUE KEY `uk_record_question_segment_31` (`record_id`, `question_index`, `segment_seq`),
  KEY `idx_audio_record_question_32` (`record_id`, `question_index`),
  KEY `idx_audio_status_33` (`status`),
  KEY `idx_audio_user_34` (`user_id`)
);

CREATE TABLE IF NOT EXISTS `interview_audio_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL,
  `segment_id` BIGINT DEFAULT NULL,
  `question_index` INT NOT NULL DEFAULT 1,
  `event_type` VARCHAR(64) NOT NULL,
  `operator_id` BIGINT DEFAULT NULL,
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `payload_json` LONGTEXT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_audio_event_record_question_35` (`record_id`, `question_index`),
  KEY `idx_audio_event_segment_36` (`segment_id`),
  KEY `idx_audio_event_type_37` (`event_type`)
);

CREATE TABLE IF NOT EXISTS `interview_video_frame` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `question_index` INT NOT NULL DEFAULT 1,
  `frame_seq` BIGINT NOT NULL,
  `key_frame` TINYINT DEFAULT 0,
  `timestamp_ms` BIGINT NOT NULL,
  `captured_at` DATETIME NOT NULL,
  `width` INT NOT NULL,
  `height` INT NOT NULL,
  `brightness` DECIMAL(8,4) DEFAULT NULL,
  `sharpness` DECIMAL(10,4) DEFAULT NULL,
  `diff_ratio` DECIMAL(8,4) DEFAULT NULL,
  `file_url` VARCHAR(512) DEFAULT NULL,
  `thumbnail_url` VARCHAR(512) DEFAULT NULL,
  `mime_type` VARCHAR(64) DEFAULT 'image/jpeg',
  `file_size` BIGINT DEFAULT 0,
  `status` VARCHAR(32) DEFAULT 'CREATED',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
  UNIQUE KEY `uk_record_question_frame_38` (`record_id`, `question_index`, `frame_seq`),
  KEY `idx_video_frame_record_question_39` (`record_id`, `question_index`),
  KEY `idx_video_frame_key_40` (`record_id`, `question_index`, `key_frame`),
  KEY `idx_video_frame_status_41` (`status`)
);

CREATE TABLE IF NOT EXISTS `interview_video_segment` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `question_index` INT NOT NULL DEFAULT 1,
  `segment_seq` INT NOT NULL DEFAULT 1,
  `start_at` DATETIME NOT NULL,
  `end_at` DATETIME DEFAULT NULL,
  `duration_ms` INT DEFAULT 0,
  `file_url` VARCHAR(512) DEFAULT NULL,
  `thumbnail_url` VARCHAR(512) DEFAULT NULL,
  `mime_type` VARCHAR(64) DEFAULT NULL,
  `file_size` BIGINT DEFAULT 0,
  `width` INT DEFAULT NULL,
  `height` INT DEFAULT NULL,
  `frame_rate` INT DEFAULT NULL,
  `bitrate` INT DEFAULT NULL,
  `network_level` VARCHAR(32) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'RECORDING',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ,
  UNIQUE KEY `uk_video_record_question_segment_42` (`record_id`, `question_index`, `segment_seq`),
  KEY `idx_video_segment_record_question_43` (`record_id`, `question_index`),
  KEY `idx_video_segment_status_44` (`status`)
);

CREATE TABLE IF NOT EXISTS `interview_video_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL,
  `segment_id` BIGINT DEFAULT NULL,
  `frame_id` BIGINT DEFAULT NULL,
  `question_index` INT NOT NULL DEFAULT 1,
  `event_type` VARCHAR(64) NOT NULL,
  `operator_id` BIGINT DEFAULT NULL,
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `payload_json` LONGTEXT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_video_event_record_question_45` (`record_id`, `question_index`),
  KEY `idx_video_event_segment_46` (`segment_id`),
  KEY `idx_video_event_frame_47` (`frame_id`),
  KEY `idx_video_event_type_48` (`event_type`)
);







CREATE TABLE IF NOT EXISTS `interview_vision_metric` (
  `id`               BIGINT PRIMARY KEY AUTO_INCREMENT,
  `record_id`        BIGINT      NOT NULL,
  `session_id`       VARCHAR(64) NOT NULL,
  `question_index`   INT         NOT NULL,
  `timestamp`        DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `expression`       VARCHAR(20),
  `expression_score` DECIMAL(5,1),
  `attention_score`  DECIMAL(5,1),
  `face_detected`    TINYINT(1)  DEFAULT 0,
  `created_at`       DATETIME    DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_vision_metric_record_49` (`record_id`, `question_index`),
  KEY `idx_vision_metric_session_50` (`session_id`)
);

CREATE TABLE IF NOT EXISTS `interview_vision_summary` (
  `id`                BIGINT PRIMARY KEY AUTO_INCREMENT,
  `record_id`         BIGINT      NOT NULL,
  `session_id`        VARCHAR(64) NOT NULL,
  `expression_score`  DECIMAL(5,1),
  `attention_score`   DECIMAL(5,1),
  `video_available`   TINYINT(1)  DEFAULT 0,
  `face_frames`       INT,
  `duration_seconds`  DECIMAL(8,1),
  `payload_json`      LONGTEXT,
  `created_at`        DATETIME    DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_vision_summary_record_51` (`record_id`),
  UNIQUE KEY `uk_vision_summary_session_52` (`session_id`)
);






CREATE TABLE IF NOT EXISTS `interview_transcript` (
  `id`             BIGINT       PRIMARY KEY AUTO_INCREMENT,
  `record_id`      BIGINT       NOT NULL,
  `question_index` INT          NOT NULL,
  `segment_id`     BIGINT       NOT NULL,
  `text`           TEXT,
  `language`       VARCHAR(20),
  `segments_json`  LONGTEXT,
  `status`         VARCHAR(20)  DEFAULT 'PARSED',
  `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_record_q_53` (`record_id`, `question_index`)
);

CREATE TABLE IF NOT EXISTS `interview_transcript_segment` (
  `id`            BIGINT   PRIMARY KEY AUTO_INCREMENT,
  `transcript_id` BIGINT   NOT NULL,
  `seq`           INT      NOT NULL,
  `start_ms`      BIGINT   NOT NULL,
  `end_ms`        BIGINT   NOT NULL,
  `text`          VARCHAR(500),
  `confidence`    DECIMAL(5,4),
  KEY `idx_transcript_54` (`transcript_id`)
);

CREATE TABLE IF NOT EXISTS user_oauth_binding (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 provider VARCHAR(40) NOT NULL, provider_user_id VARCHAR(120) NOT NULL,
 provider_username VARCHAR(120), avatar_url VARCHAR(500),
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY `uk_oauth_provider_55` (provider, provider_user_id)
);
