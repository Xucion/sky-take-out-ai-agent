import sqlalchemy as sa
from alembic import op
from sqlalchemy import inspect
from sqlalchemy.dialects.mysql import VARCHAR

from sky_ai.database import now

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade():
    """添加处理者和租约列，并让迁移前遗留的 PENDING 消息立即具备可恢复条件。"""
    connection = op.get_bind()
    inspector = inspect(connection)
    columns = {column["name"] for column in inspector.get_columns("ai_message")}
    if "processing_owner" not in columns:
        owner_type = sa.String(64).with_variant(
            VARCHAR(64, charset="ascii", collation="ascii_bin"), "mysql"
        )
        op.add_column("ai_message", sa.Column("processing_owner", owner_type, nullable=True))
    if "lease_expires_at" not in columns:
        op.add_column("ai_message", sa.Column("lease_expires_at", sa.DateTime(), nullable=True))

    inspector = inspect(connection)
    indexes = {index["name"] for index in inspector.get_indexes("ai_message")}
    if "ix_ai_message_lease_expires_at" not in indexes:
        op.create_index(
            "ix_ai_message_lease_expires_at", "ai_message", ["lease_expires_at"], unique=False
        )

    connection.execute(
        sa.text(
            "UPDATE ai_message SET lease_expires_at = :expired "
            "WHERE role = 'ASSISTANT' AND status = 'PENDING' AND lease_expires_at IS NULL"
        ),
        {"expired": now()},
    )


def downgrade():
    """保留消息数据，只移除本版本新增的可选租约结构。"""
    op.drop_index("ix_ai_message_lease_expires_at", table_name="ai_message")
    op.drop_column("ai_message", "lease_expires_at")
    op.drop_column("ai_message", "processing_owner")
