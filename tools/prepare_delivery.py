"""Build derived schema and packaging inputs without changing user source documents."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
sql_dir = ROOT / 'interview-ai/sql'
full = (sql_dir / 'interview_ai.sql').read_text(encoding='utf-8-sig')
extras = '''
CREATE TABLE IF NOT EXISTS user_oauth_binding (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 provider VARCHAR(40) NOT NULL, provider_user_id VARCHAR(120) NOT NULL,
 provider_username VARCHAR(120), avatar_url VARCHAR(500),
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_oauth_provider (provider, provider_user_id)
);
'''
if '`report_json`' not in full:
    full = full.replace('`submit_id` VARCHAR(64)', '`report_json` LONGTEXT COMMENT \'可追溯评测报告\',\n    `submit_id` VARCHAR(64)')
if 'CREATE TABLE IF NOT EXISTS `interview_transcript`' not in full:
    full += '\n' + (sql_dir / 'interview_transcript_delta.sql').read_text(encoding='utf-8-sig')
if 'CREATE TABLE IF NOT EXISTS user_oauth_binding' not in full:
    full += extras
(sql_dir / 'interview_ai.sql').write_text(full, encoding='utf-8')
(sql_dir / 'acceptance_v110_delta.sql').write_text(
    "-- 仅已有数据库执行一次；全新库只执行 interview_ai.sql\n"
    "ALTER TABLE interview_record ADD COLUMN report_json LONGTEXT;\n" + extras +
    (sql_dir / 'interview_transcript_delta.sql').read_text(encoding='utf-8-sig'), encoding='utf-8')

# H2 demo uses the same tables and demo seed data. Persist in a dedicated local file.
h2 = re.sub(r'--[^\n]*', '', full)
h2 = re.sub(r'CREATE DATABASE.*?;', '', h2, flags=re.S)
h2 = re.sub(r'USE `interview_ai`;', '', h2)
h2 = re.sub(r'\)\s*ENGINE=InnoDB[^;]*;', ');', h2)
h2 = re.sub(r"\s+COMMENT\s*'[^']*'", '', h2)
h2 = re.sub(r'\bJSON\b', 'LONGTEXT', h2)
h2 = re.sub(r'ON UPDATE CURRENT_TIMESTAMP', '', h2)
h2 = re.sub(r'CREATE TABLE (?!IF NOT EXISTS)', 'CREATE TABLE IF NOT EXISTS ', h2)
index_counter = iter(range(1000))
h2 = re.sub(r'((?:UNIQUE\s+)?(?:INDEX|KEY)\s+)(`?)(\w+)\2(?=\s*\()',
            lambda m: m[1] + '`' + m[3] + '_' + str(next(index_counter)) + '`', h2)
# Expand MySQL multi-clause ALTER for H2.
def alter(match):
    table, body = match.group(1), match.group(2)
    parts = re.split(r',\s*(?=ADD )', body)
    return '\n'.join(f'ALTER TABLE {table} {p.strip()};' for p in parts)
h2 = re.sub(r'ALTER TABLE (`\w+`)\s+(.*?);', alter, h2, flags=re.S)
h2 = '\n'.join(line.rstrip() for line in h2.splitlines()) + '\n'
# Startup schema runs on a new H2 file only, using Spring's embedded initialization.
resources = ROOT / 'interview-ai/interview-bootstrap/src/main/resources'
(resources / 'schema-demo.sql').write_text(h2, encoding='utf-8')
print('Prepared MySQL full/migration scripts and H2 demo schema')
