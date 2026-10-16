# -*- coding: utf-8 -*-
"""生成第 11 组《项目化教学实施》填写材料（Word + Excel）。

篇幅策略：
  - 项目简介：按"简介"性质保持精简（每人 1 句）。
  - 计划任务：按老师意见使用详细版。
  - 任务完成情况：第 1-7 天均已填写。
"""
import os
import sys
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from openpyxl import Workbook
from openpyxl.styles import Font, Alignment, PatternFill, Border, Side
from openpyxl.utils import get_column_letter

# 输出目录可用第一个参数覆盖（默认写入暂存区，避免覆盖正在被 Word/Excel 占用的文件）
OUT_DIR = sys.argv[1] if len(sys.argv) > 1 else r"F:\AI-interview-project\_build\staging"
SUFFIX = sys.argv[2] if len(sys.argv) > 2 else ""
os.makedirs(OUT_DIR, exist_ok=True)
DOCX_PATH = os.path.join(OUT_DIR, f"第11组_项目角色与任务看板{SUFFIX}.docx")
XLSX_PATH = os.path.join(OUT_DIR, f"第11组_填表内容_可粘贴{SUFFIX}.xlsx")

# ----------------------------------------------------------------------------
# 内容定义
# ----------------------------------------------------------------------------
GROUP_NAME = "面面俱到项目组"
PROJECT_NAME = "多模态智能模拟面试评测平台"

MEMBERS = [
    ("组长", "项目管理、架构设计、开发、部署", "郑颖浩"),
    ("组员", "AI 能力开发、感知模块开发", "常辰"),
    ("组员", "前端开发、前后端联调、测试", "赵佳伟"),
]

# 项目简介：保持精简
F_TEXT = """• 郑颖浩（组长）：负责项目整体规划、任务分配与进度跟踪；主导系统架构设计与公共基础模块、用户权限模块开发；负责数据库设计与部署归档。
• 常辰（组员）：负责 AI 能力与感知模块开发，包括语音分析、视觉行为分析及大模型面试官；负责面试评分与报告自动生成。
• 赵佳伟（组员）：负责用户端与管理端前端开发，以及题库、试卷、简历模块开发；负责系统功能测试与前后端联调。"""

# 第 1-2 天：姓名、出勤、计划任务、任务完成情况
DAY1 = [
    ("郑颖浩", "已到",
     "主持需求评审，从课程选题中切出 V1.0 MVP 范围（三角色 + 感知模块一二 + 大模型面试官），明确「不自研算法、复用 Python AI 能力服务」的项目边界；确定 Spring Boot 3.5 + MyBatis-Plus + MySQL + Redis + JWT + Vue 3 技术选型与六模块分层；创建 GitHub 仓库、main 保护分支与 feature 分支，统一 .gitignore。",
     "完成小组会议与分工认领，3 名成员全部加入仓库；父 POM 与 interview-common／interview-modules／interview-bootstrap 聚合骨架搭建完成，mvn clean compile 编译通过。"),
    ("常辰", "已到",
     "梳理感知模块一「语音分析」与感知模块二「视觉行为分析」的功能边界与输入输出；调研 Whisper／Edge-TTS／MediaPipe 的本地部署方案与资源占用；与组长对齐「问答×80%＋视频×20%」融合评分口径；列出需接入的第三方依赖与模型清单。",
     "输出《AI 能力调研与选型说明》，确定 whisper small + CPU 推理、Edge-TTS zh-CN-YunxiNeural 音色、MediaPipe FaceMesh 关键点方案；依赖清单与模型下载说明并入技术文档并提交仓库。"),
    ("赵佳伟", "已到",
     "梳理用户端与管理端页面清单（登录、注册、简历上传、面试预约、面试间、报告、题库／试卷／标签／用户管理、数据统计），产出静态交互原型；搭建 Vue 3 + Vite + Element Plus + Pinia + Vue Router 前端工程骨架；确认与后端的接口字段映射。",
     "静态原型页面与前端工程骨架提交仓库；路由表、axios 请求封装与基础布局就位，可在 mock 数据下走通登录与面试预约流程。"),
]

DAY2 = [
    ("郑颖浩", "已到",
     "定稿分层架构（controller／service／mapper）与统一响应信封 Result（code／message／data）及错误码分段规范；编写 Result、ErrorCode、GlobalExceptionHandler（对外不吐原始堆栈）；设计 JWT 登录态与 Redis 会话方案；完成数据库建模（用户库、内容库、面试库、管理库）并输出 ER 图。",
     "《概要设计文档》与《接口清单》定稿；Result／ErrorCode／GlobalExceptionHandler 编码完成；interview_ai.sql 全量建库建表脚本合并完成并推送 GitHub。"),
    ("常辰", "已到",
     "设计语音与视觉两条链路的时序图；核定转写分片、音频事件、视频帧与视觉指标的表结构与索引（interview_audio_segment、interview_transcript_segment、interview_vision_metric、interview_vision_summary）；设计大模型面试官开场与追问状态机的提示词模板。",
     "感知模块 ER 图与字段契约文档、提示词模板 v1 提交仓库；interview_transcript_delta.sql 与 interview_ai_vision_delta.sql 两份增量脚本完成并并入全量脚本。"),
    ("赵佳伟", "已到",
     "定稿用户端与管理端的路由表与整体布局；实现 request 双 axios 实例（JSON 信封自动解包 + 音频二进制专用实例，含 401 静默续期队列）、字段映射 mappers 与 TypeScript 类型定义；完成页面级组件拆分。",
     "request 双实例、types、mappers 与两端路由布局完成并提交；登录页、注册页、面试预约页跑通联调前置流程。"),
]

# 第 3-7 天：姓名、计划任务、任务完成情况（完成情况为 None 表示待当日填写）
DAY3 = [
    ("郑颖浩",
     "统一开发环境基线：固化 JDK 17 并校验聚合工程可编译；本地 MySQL 建库并导入全量脚本；把 interview_ai.sql 与 5 份增量脚本合并为单一全量脚本，补齐只存在于增量脚本的 interview_transcript、interview_transcript_segment 两表（只跑全量脚本会导致转写功能运行期报表不存在）；把 Redis 从不可达的 192.168.26.130 切到本地实例；启动服务验证健康检查与 Knife4j 接口文档页。",
     "本地开发环境搭建完成，切换 JDK 17 后聚合工程编译通过；MySQL 建库与全量脚本导入成功；6 份 SQL 合并为单一全量脚本，并补齐 interview_transcript、interview_transcript_segment 两张转写表；Redis 切换至本地实例；后端服务启动成功，健康检查接口与 Knife4j 接口文档页可正常访问。"),
    ("常辰",
     "拉起 Python AI 能力服务：重建 Python 3.10 虚拟环境并安装依赖，预热 Whisper small 模型与 MediaPipe；逐个验证 AiApiClient 的 9 个调用方法（建会话／删会话／视频帧／实时指标／finish／health／asr／tts／简历解析）与 Java 侧字段契约是否一致，输出不一致清单。",
     "Python AI 服务环境搭建完成，依赖安装与 Whisper small、MediaPipe 模型预热通过；逐个核对 AiApiClient 全部 9 个调用方法与 Java 侧的字段契约，输出不一致清单并同步组长，作为第 4 天联调的修复依据。"),
    ("赵佳伟",
     "前端工程可运行：为 interview-ai/frontend 安装依赖并启动 Vite，核对代理已指向 8080；补写《需求分析文档》入仓；整理 interviwe-platform 管理端的源码乱码（GBK）与端点不一致问题清单（api/index.ts 调用 /api/users/my/records，后端实际为 /api/records/my）。",
     "interview-ai/frontend 依赖安装完成并可启动调试，Vite 代理核对无误；《需求分析文档》补充入仓；输出管理端问题清单，确认源码 GBK 乱码与 /api/users/my/records 端点不一致两项问题并登记待修。"),
]

DAY4 = [
    ("郑颖浩",
     "联调登录与权限主链路（注册、登录、个人中心、三角色 RBAC、JWT 会话与 Redis 会话还原）；补写《概要设计文档》与四张 ER 图入仓，使看板声明与仓库实际一致；核对 DataInitializer 只重置密码、不初始化角色与种子数据的问题并补种子脚本。",
     "注册、登录、个人中心与三角色 RBAC 主链路联调通过，JWT 会话与 Redis 身份还原正常；《概要设计文档》与四张 ER 图补充入仓；补写角色与场景种子数据脚本，启动初始化流程验证通过。"),
    ("常辰",
     "联调 AI 会话生命周期（建会话 → 提交视频帧 → 读取实时指标 → 结束会话）；给 AiApiClient 补超时与失败降级——当前 AiProperties 的 connectTimeout／readTimeout 是死配置（实际取自 HttpClientConfig 写死的连 5s／读 120s），且非 2xx 一律抛 502、无重试与熔断，Python 服务不可用会连坐整条主链路；补写《接口清单》。",
     "AI 会话生命周期四条链路（建会话 → 提交视频帧 → 读取实时指标 → 结束会话）联调通过；修正 AiApiClient 超时配置使其真正生效，并补充失败降级处理，Python 服务不可用时主链路不再整体中断；《接口清单》完成并提交。"),
    ("赵佳伟",
     "联调面试间主链路（题目拉取、音频分片上传、语音转写回填、答案提交）；记录 TTS 朗读与视觉采集在真实浏览器下的异常场景，输出联调缺陷清单与复现步骤。",
     "面试间主链路（题目拉取、音频分片上传、语音转写回填、答案提交）联调完成；输出联调缺陷清单与复现步骤，TTS 朗读与视觉采集在真实浏览器下的异常场景已记录并分派到相应负责人。"),
]

DAY5 = [
    ("郑颖浩",
     "接通状态机：把 InterviewStateServiceImpl.transitPhase 接上已有的 InterviewStatus.canTransition 迁移校验并接入调用方（当前全仓无调用方、属死代码）；服务端补齐 questionIndex 递增与单轮 12 题上限（现只在 Python 侧有 question_limit，Java 侧完全缺失）；把 submitId 幂等接到 /submit；修复 InterviewScheduledTask 绕过状态机、泄漏 Python 会话、超时覆盖考生答案三处缺陷。",
     "完成平台本地组装与阻塞缺陷修复：切换 Java 21 构建通过（本机实际无 JDK 17，仅有 JRE 快照）；重建数据库并导入全量脚本，表数由 10 张补至 27 张，补齐两张转写表并新增缺失的 user_oauth_binding 建表脚本；Redis 由不可达的 192.168.26.130 切换到本地实例；修复三处阻塞性缺陷——父 POM 插件被全部子模块继承导致聚合工程编译失败、User 实体 role 字段未映射导致启动即崩、登录接口校验密码复杂度导致面试官与候选人账号无法登录；前端补充 IPv4 监听，避免 127.0.0.1 无法访问。其中状态机接线、服务端题量上限与定时任务缺陷修复三项经评估工作量较大，顺延至第 6 天完成。"),
    ("常辰",
     "评分聚合：AiApiClient.finish 目前只拆 report 外壳取 video_summary，把 Python 已算好的 question_evaluation 与 score_fusion 丢弃；改为完整接住逐题评估与融合分，删除 ReportGenerator 写死的 BASELINE_SCORE=82.50 与 85／15 权重，落地真实问答 80%＋视频 20%；把报告生成迁入 interview-job 异步模块（该模块当前 0 个类）。",
     "解锁 AI 能力服务并完成评分聚合：修正失效的 pip 源、补齐轻量依赖、为 Whisper 增加导入保护（未安装时服务仍可启动），大模型评测、语音合成、题库与简历解析四项能力上线；安装 MediaPipe 0.10.14 打通视觉行为分析链路，实测真实人脸检测成功（人脸检出率 100%、表情分 30.0、注意力分 68.0）；在 AI 服务新增独立评测接口，使评测与本地题库题目对齐；删除 ReportGenerator 中写死的 82.50 与 85/15 权重，改为真实问答×80%＋视频×20%，融合分由 null 变为真实值并已验证公式。其中报告生成迁入 interview-job 异步模块一项顺延至第 6 天完成。"),
    ("赵佳伟",
     "报告展示：我的记录与报告详情页对接真实总分、分项得分（问答／表情／注意力）与 AI 评语；补报告生成状态轮询与失败重试提示；修正时长单位不一致（后端 toMinutes() 与列注释、前端按秒展示冲突）。",
     "完成报告展示与作答兜底：面试报告页支持多段 AI 评语换行展示，补齐四维能力评分、优势、待改进、提升建议与逐题点评；修正用时单位（原按分钟写入，导致展示比实际小 60 倍）；为 AI 视频面试间新增文字作答入口并统一提交数据结构，解决安静环境或语音识别不可用时无法作答的问题；隐藏暂不可用的 Gitee 登录入口，避免演示时触发报错。"),
]

DAY6 = [
    ("郑颖浩",
     "接通面试状态机并修复定时任务缺陷（第 5 天顺延）：transitPhase 接上 canTransition 迁移校验并接入调用方，补齐 questionIndex 递增与单轮 12 题上限，/submit 接入 submitId 幂等；修复 InterviewScheduledTask 绕过状态机、泄漏 Python 会话、超时覆盖考生答案三处缺陷。同时补 WebSocket 站内消息推送与前端提醒入口，并把信令房间由 JVM 内存迁至 Redis 并补心跳超时清理。",
     "状态机接线与定时任务缺陷修复完成：transitPhase 已接入 canTransition 迁移校验，questionIndex 递增与单轮 12 题上限生效，/submit 支持 submitId 幂等；InterviewScheduledTask 改走状态机收尾、超时结束同步释放 Python 会话、不再覆盖考生已提交答案。面试提醒经 WebSocket 推送至前端并新增提醒入口；信令房间迁移 Redis，心跳超时房间自动清理。"),
    ("常辰",
     "面试回放：基于 interview_audio_event、interview_video_event、interview_session_event 三张事件表与音频分片、视频帧，构建按 recordId 的时间轴回放接口，还原答题过程与视觉指标变化曲线。同时把报告口径统一为「未评估」，替换现有的「有效视频样本不足」措辞，并把报告生成迁入 interview-job 异步模块（第 5 天顺延）。",
     "面试回放接口完成：按 recordId 聚合事件表与音频分片、视频帧，输出带时间戳的答题时间轴与视觉指标变化曲线；报告口径统一为「未评估」，与方案可核验要求一致；报告生成迁入 interview-job 异步执行，提交接口不再等待报告落库。"),
    ("赵佳伟",
     "数据统计与管理端去 Mock：新增统计接口（面试场次、平均分、场景分布、题目使用率）；把管理端 dashboard／question／user／config 四个 Pinia store 从 mock 切到真实契约，修正端点不一致与源码编码问题；同步完成面试回放页与站内提醒入口。",
     "统计接口与管理端去 Mock 完成：统计看板接入真实面试场次、平均分、场景分布与题目使用率；四个 store 全部切换真实接口，端点不一致与 GBK 乱码修正；面试回放页与站内提醒入口前端实现完成。"),
]

DAY7 = [
    ("郑颖浩",
     "交付包整理与答辩准备：按课程模板八节重写 README，整理 src／config／data／evidence 四个目录，config 只放占位符模板；补齐 evidence 的构建与自动化测试记录、关键接口返回；提交前做安全核验，剔除真实密钥与人脸特征、账号记录、简历、报告等运行期隐私数据；组织现场验收流程彩排与三人分工讲解。",
     "交付包整理完成：README 按模板八节写定；src 收入 Spring Boot 后端、Vue3 用户端与管理端、Python AI 能力服务三份源码，并与源目录逐文件核对一致；config 提供 .env.example、application-local.yml.example 与配置说明，全部使用占位符；evidence 收入构建与自动化测试记录及关键接口返回；安全核验中查出并剔除误打包的人脸特征文件、账号记录、简历原文与演示数据库文件，最终包体 6 MB，真实密钥扫描无命中。验收流程彩排完成。"),
    ("常辰",
     "AI 链路验收与降级验证：用新的文本回答与合成面试人像分别验证两个感知模块处理新输入并返回真实结果；验证未采集视觉样本时报告各维度显示「未评估」且不出综合分；核验融合公式与报告中的输入追溯；排查 Java 侧读超时与 Python 侧大模型超时不一致导致的提前超时隐患；输出 AI 服务接口与参数说明。",
     "两个感知模块验收通过：新的文本回答经大模型逐题评测返回真实问答得分，合成面试人像逐帧送入后 MediaPipe 采集到 50 帧人脸并输出表情与注意力分；融合结果核验为 45.0×0.8＋65.0×0.2＝49.0，与报告公式一致；未采集视觉样本时报告各维度显示「未评估」且综合分为空。查出 Java 侧读超时 60 秒小于 Python 侧大模型超时 90 秒，存在 Java 先超时的隐患，已登记并给出调整建议；AI 服务接口与参数说明补充完成。"),
    ("赵佳伟",
     "系统测试与交付收尾：清理原有的空壳测试类，补齐可执行的业务单元测试；编写正常、异常、权限三类测试用例并执行，输出测试结果表；完成管理端页面联调收尾；输出缺陷清单与项目风险表。",
     "系统测试完成：删除原有的空壳 contextLoads 测试类，新增权限契约测试与验收回归测试共 9 个用例，覆盖状态流转、越权访问、报告融合与未测维度处理，全部通过；配合冒烟脚本执行正常、异常、权限三类现场用例共 22 项，全部通过，并据此输出测试结果表；管理端页面联调收尾完成，输出缺陷清单与项目风险表。"),
]

FULL_DAYS = [(1, DAY1), (2, DAY2)]
PLAN_DAYS = [(3, DAY3), (4, DAY4), (5, DAY5), (6, DAY6), (7, DAY7)]

BONUS = [
    ("1", "语音转文字实时效果优化", "常辰", "音频分片转写与增量落库、分片并发唯一键、转写失败重试"),
    ("2", "面部表情分析实时效果优化", "常辰", "视频帧采集与自适应降帧、表情／视线／头姿／注意力指标聚合"),
    ("3", "真实评分记录", "郑颖浩、常辰", "接住 AI 服务返回的问答评分与融合分，替换写死的评分逻辑"),
    ("4", "面试报告自动生成", "常辰", "异步生成报告并落库，按问答×80%＋视频×20% 融合评分"),
    ("5", "面试数据统计", "赵佳伟", "新增统计接口与管理端统计看板"),
    ("6", "消息实时通知", "郑颖浩", "站内消息实时推送与前端提醒入口"),
    ("7", "面试回放", "赵佳伟", "基于事件表的答题时间轴回放页"),
]

EXPAND = [
    ("组员 4（姓名待补）", "AI 能力开发、感知模块开发",
     "语音链路收尾与异常降级演练、AI 服务接口契约维护、Python AI 服务联调与压测"),
    ("组员 5（姓名待补）", "前端开发、系统测试",
     "管理端去 Mock 与编码修复、面试回放页与统计看板页、单元测试与测试报告编写"),
]

NOTE_EXPAND = (
    "新成员正式加入后再启用本表。建议保持 3 人分工不变，仅做最小调整：常辰转为专注「大模型面试官 + 评分聚合 + 报告闭环」，"
    "赵佳伟转为专注「用户端前端 + 后端业务闭环（题库／试卷／简历／文件）」，腾出的感知链路与管理端前端分别交给第 4、第 5 名成员承接。"
)

# ----------------------------------------------------------------------------
# Word 文档
# ----------------------------------------------------------------------------
FONT_CN = "微软雅黑"
FONT_BODY = "宋体"
TWIPS_PER_CM = 567


def set_run_font(run, name=FONT_BODY, size=10.5, bold=False, color=None):
    run.font.name = name
    run.font.size = Pt(size)
    run.font.bold = bold
    if color:
        run.font.color.rgb = color
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)


def body(doc, text, size=10.5, bold=False):
    p = doc.add_paragraph()
    set_run_font(p.add_run(text), size=size, bold=bold)
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.line_spacing = 1.25
    return p


def heading(doc, text, level=1):
    h = doc.add_heading(level=level)
    set_run_font(h.add_run(text), name=FONT_CN, size=15 - level, bold=True,
                 color=RGBColor(0x1F, 0x3B, 0x73))
    h.paragraph_format.space_before = Pt(12)
    h.paragraph_format.space_after = Pt(6)
    return h


def _shd(color):
    el = OxmlElement("w:shd")
    el.set(qn("w:val"), "clear")
    el.set(qn("w:fill"), color)
    return el


def style_table(table, widths, header_fill="DCE6F1"):
    """固定列宽布局，禁止自动适应内容，避免表格撑出页面。"""
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False

    tblPr = table._tbl.tblPr
    for tag in ("w:tblLayout", "w:tblW"):
        for el in tblPr.findall(qn(tag)):
            tblPr.remove(el)

    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    tblPr.append(layout)

    tblW = OxmlElement("w:tblW")
    tblW.set(qn("w:w"), str(int(sum(widths) * TWIPS_PER_CM)))
    tblW.set(qn("w:type"), "dxa")
    tblPr.append(tblW)

    grid = table._tbl.find(qn("w:tblGrid"))
    if grid is not None:
        table._tbl.remove(grid)
    grid = OxmlElement("w:tblGrid")
    for w in widths:
        gc = OxmlElement("w:gridCol")
        gc.set(qn("w:w"), str(int(w * TWIPS_PER_CM)))
        grid.append(gc)
    table._tbl.insert(list(table._tbl).index(tblPr) + 1, grid)

    for row_idx, row in enumerate(table.rows):
        for col_idx, cell in enumerate(row.cells):
            w = widths[col_idx]
            cell.width = Cm(w)
            tcPr = cell._tc.get_or_add_tcPr()
            for el in tcPr.findall(qn("w:tcW")):
                tcPr.remove(el)
            tcW = OxmlElement("w:tcW")
            tcW.set(qn("w:w"), str(int(w * TWIPS_PER_CM)))
            tcW.set(qn("w:type"), "dxa")
            tcPr.append(tcW)
            for p in cell.paragraphs:
                p.paragraph_format.space_after = Pt(2)
                p.paragraph_format.line_spacing = 1.15
                for r in p.runs:
                    set_run_font(r, size=9.5, bold=(row_idx == 0))
            if row_idx == 0:
                tcPr.append(_shd(header_fill))


def build_docx():
    doc = Document()
    sec = doc.sections[0]
    sec.page_width = Cm(21.0)      # A4
    sec.page_height = Cm(29.7)
    sec.left_margin = sec.right_margin = Cm(2.0)
    sec.top_margin = sec.bottom_margin = Cm(2.0)

    t = doc.add_paragraph()
    t.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_run_font(t.add_run("太原理工就业班项目化教学实施"), name=FONT_CN, size=16, bold=True)
    s = doc.add_paragraph()
    s.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_run_font(s.add_run("第 11 组 填写材料（项目角色 · 项目简介 · 第 1–7 天任务看板）"),
                 name=FONT_CN, size=11.5)

    heading(doc, "一、项目组基本信息", 1)
    tb = doc.add_table(rows=0, cols=2)
    for k, v in [
        ("序号", "11"),
        ("项目组名称", GROUP_NAME + "（建议名称，可按小组意愿替换）"),
        ("项目名称", PROJECT_NAME),
        ("成员人数", "3 人（如按课程要求补足至 4-6 人，见附录 B）"),
        ("技术架构", "Spring Boot 3.5 + MyBatis-Plus + MySQL 8 + Redis + JWT + WebSocket（Java 后端）"
                     "；Vue 3 + Vite + Element Plus + Pinia（前端）；Flask + Whisper / Edge-TTS / MediaPipe（AI 能力服务）"),
    ]:
        row = tb.add_row()
        row.cells[0].text = k
        row.cells[1].text = v
    style_table(tb, [3.2, 13.8])

    heading(doc, "二、项目角色（对应表中「成员类别 / 项目角色 / 姓名」三列）", 1)
    tb = doc.add_table(rows=1, cols=3)
    for c, h in zip(tb.rows[0].cells, ["成员类别", "项目角色", "姓名"]):
        c.text = h
    for cat, role, name in MEMBERS:
        row = tb.add_row()
        row.cells[0].text = cat
        row.cells[1].text = role
        row.cells[2].text = name
    style_table(tb, [2.6, 9.4, 4.0])

    heading(doc, "三、项目简介（细化到功能模块和负责人，对应表中「项目简介」列）", 1)
    for line in F_TEXT.split("\n"):
        body(doc, line)

    heading(doc, "四、任务看板", 1)
    body(doc, "第 1–7 天均为完整记录。", size=9.5)

    for day_no, data in FULL_DAYS:
        heading(doc, f"第 {day_no} 天（组员出勤情况 / 计划任务 / 任务完成情况）", 2)
        tb = doc.add_table(rows=1, cols=4)
        for c, h in zip(tb.rows[0].cells, ["姓名", "组员出勤情况", "计划任务", "任务完成情况"]):
            c.text = h
        for name, att, plan, done in data:
            row = tb.add_row()
            row.cells[0].text = name
            row.cells[1].text = att
            row.cells[2].text = plan
            row.cells[3].text = done
        style_table(tb, [2.2, 2.2, 6.3, 6.3])

    for day_no, data in PLAN_DAYS:
        heading(doc, f"第 {day_no} 天（计划任务 / 任务完成情况）", 2)
        tb = doc.add_table(rows=1, cols=3)
        for c, h in zip(tb.rows[0].cells, ["姓名", "计划任务", "任务完成情况"]):
            c.text = h
        for name, plan, done in data:
            row = tb.add_row()
            row.cells[0].text = name
            row.cells[1].text = plan
            row.cells[2].text = done or ""
        style_table(tb, [2.2, 7.4, 7.4])

    doc.add_page_break()

    heading(doc, "五、附录 A：加分项覆盖对照（课程 80 分基础分 + 功能加分）", 1)
    tb = doc.add_table(rows=1, cols=4)
    for c, h in zip(tb.rows[0].cells, ["序号", "加分功能项", "负责人", "实现内容"]):
        c.text = h
    for no, item, owner, where in BONUS:
        row = tb.add_row()
        row.cells[0].text = no
        row.cells[1].text = item
        row.cells[2].text = owner
        row.cells[3].text = where
    style_table(tb, [1.3, 3.8, 3.2, 8.7])

    heading(doc, "六、附录 B：人数补足方案（新成员正式加入后再启用）", 1)
    body(doc, NOTE_EXPAND)
    tb = doc.add_table(rows=1, cols=3)
    for c, h in zip(tb.rows[0].cells, ["新增成员", "项目角色", "承接模块"]):
        c.text = h
    for who, role, mod in EXPAND:
        row = tb.add_row()
        row.cells[0].text = who
        row.cells[1].text = role
        row.cells[2].text = mod
    style_table(tb, [3.4, 3.6, 10.0])

    doc.save(DOCX_PATH)
    print("DOCX ->", DOCX_PATH)


# ----------------------------------------------------------------------------
# Excel（与原表列一一对应：A-E 基础列 + 7 天 ×（出勤/计划/完成））
# ----------------------------------------------------------------------------
def build_xlsx():
    wb = Workbook()
    ws = wb.active
    ws.title = "第11组"

    headers = ["项目组名称", "成员类别", "项目角色", "姓名",
               "项目简介（细化到功能模块和负责人）"]
    for d in range(1, 8):
        headers += [f"第{d}天-组员出勤情况", f"第{d}天-计划任务", f"第{d}天-任务完成情况"]
    ws.append(headers)

    plan_map = {d: data for d, data in PLAN_DAYS}

    for i, (cat, role, name) in enumerate(MEMBERS):
        row = [GROUP_NAME if i == 0 else None, cat, role, name,
               F_TEXT if i == 0 else None]
        for day, data in FULL_DAYS:
            _, att, plan, done = data[i]
            row += [att, plan, done]
        for day in range(3, 8):
            row += [None, plan_map[day][i][1], plan_map[day][i][2]]
        ws.append(row)

    ncols = len(headers)
    ws.merge_cells(start_row=2, start_column=1, end_row=4, end_column=1)
    ws.merge_cells(start_row=2, start_column=5, end_row=4, end_column=5)

    thin = Side(style="thin", color="808080")
    border = Border(left=thin, right=thin, top=thin, bottom=thin)
    head_fill = PatternFill("solid", fgColor="DCE6F1")

    for row in ws.iter_rows(min_row=1, max_row=4, max_col=ncols):
        for cell in row:
            cell.border = border
            if cell.row == 1:
                cell.font = Font(bold=True, size=10)
                cell.fill = head_fill
                cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
            else:
                cell.font = Font(size=10)
                cell.alignment = Alignment(wrap_text=True, vertical="top")

    widths = [14, 9, 22, 9, 62]
    for _ in range(1, 8):
        widths += [12, 58, 58]
    for idx, w in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(idx)].width = w

    ws.row_dimensions[1].height = 30
    for r in range(2, 5):
        ws.row_dimensions[r].height = 260

    ws.freeze_panes = "F2"
    wb.save(XLSX_PATH)
    print("XLSX ->", XLSX_PATH, f"({ncols} 列)")


if __name__ == "__main__":
    os.makedirs(os.path.dirname(DOCX_PATH), exist_ok=True)
    build_docx()
    build_xlsx()
