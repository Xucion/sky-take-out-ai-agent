"""Create AI tables or adopt the Java Flyway V1/V2 schema without altering existing data."""

from pathlib import Path

from alembic import op
from sqlalchemy import inspect

from sky_ai.database import metadata

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

V2_REQUIRED_COLUMNS = {
    "ai_conversation": {
        "id", "version", "create_time", "update_time", "conversation_id", "user_id",
        "channel", "title", "status", "current_handler", "related_order_id",
        "last_message_sequence", "last_message_time", "started_at", "ended_at",
    },
    "ai_message": {
        "id", "version", "create_time", "update_time", "message_id", "conversation_id",
        "sequence_no", "role", "content", "status", "client_request_id",
        "request_fingerprint", "reply_to_message_id", "provider", "model_name",
        "input_tokens", "output_tokens", "latency_ms", "error_code", "trace_id",
    },
    "ai_tool_call": {
        "id", "version", "create_time", "update_time", "tool_call_id", "conversation_id",
        "message_id", "tool_name", "parameter_summary", "result_status", "result_code",
        "result_summary", "latency_ms", "idempotency_key", "trace_id",
    },
}


def upgrade():
    """创建 AI 表或接管已完成 V1/V2 的既有结构，保留原数据并拒绝不完整表结构。

    空 MySQL 使用随迁移保存的原始 SQL；其他数据库通过 SQLAlchemy 创建测试表。
    """
    connection = op.get_bind()
    inspector = inspect(connection)
    present = []
    for table in metadata.sorted_tables:
        if inspector.has_table(table.name):
            present.append(table.name)
    if present and len(present) != len(metadata.tables):
        raise RuntimeError("Incomplete AI schema; finish the existing migration before adoption")
    # Refuse partial/old schemas rather than silently declaring an incompatible schema migrated.
    for table_name, required_columns in V2_REQUIRED_COLUMNS.items():
        if inspector.has_table(table_name):
            columns = set()
            for column in inspector.get_columns(table_name):
                columns.add(column["name"])
            missing = required_columns - columns
            if missing:
                raise RuntimeError(
                    f"{table_name} lacks {sorted(missing)}; apply Java Flyway V2 first"
                )
    if not present and connection.dialect.name == "mysql":
        # Preserve the original MySQL defaults, indexes, precision and ASCII key collations.
        # These are fixed repository SQL scripts, never user-supplied SQL.
        for name in ("V1.sql", "V2.sql"):
            script = (Path(__file__).parents[1] / "sql" / name).read_text(encoding="utf-8")
            for statement in script.split(";"):
                if statement.strip():
                    connection.exec_driver_sql(statement)
    else:
        metadata.create_all(connection, checkfirst=True)


def downgrade():
    """拒绝破坏性的表回退操作，避免删除已有会话、消息及工具审计数据。"""
    raise RuntimeError("AI conversation data is retained; destructive downgrade is not supported")
