-- ============================================================
-- 题库补数：增加标签与题目，保证智能组卷可测试
-- 适用：已应用第04讲题库建表的基础上，向现有库填充更多标签与题目。
-- 说明：标签与标签关系使用 INSERT IGNORE（按唯一键去重），题目为一性写入，请勿重复执行。
-- ============================================================

-- 1) 追加标签（KNOWLEDGE + JOB），ID 从 16 起
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

-- 2) 追加 Java 后端场景（scenario_id=1）题目，ID 从 31 起，难度均衡（简单5/中等5/困难5）
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

-- 3) 追加题目与标签关系（题目 31-45，标签 ID 1-27）
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

-- 4) 重算各场景题目数量
UPDATE `interview_scenario` s
SET s.`question_count` = (
    SELECT COUNT(*)
    FROM `interview_question` q
    WHERE q.`scenario_id` = s.`id`
      AND q.`status` = 1
);