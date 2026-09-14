"""SQLAlchemy mappings for the existing V1/V2 AI tables; no business tables."""

from datetime import datetime, timedelta, timezone
from hashlib import sha256
from uuid import uuid4

from sqlalchemy import (
    JSON,
    BigInteger,
    Column,
    DateTime,
    ForeignKey,
    Integer,
    MetaData,
    String,
    Table,
    Text,
    UniqueConstraint,
    create_engine,
    event,
    insert,
    select,
    update,
)
from sqlalchemy.dialects.mysql import MEDIUMTEXT, VARCHAR

from sky_ai.security import ApiError

metadata = MetaData()
pk_type = BigInteger().with_variant(Integer, "sqlite")
DATABASE_TIMEZONE = timezone(timedelta(hours=8), "Asia/Shanghai")


def uid():
    """生成用于会话、消息或工具审计的随机 UUID 字符串。"""
    return str(uuid4())


def now():
    """返回 Asia/Shanghai 的无时区时间，与旧 Java LocalDateTime 数据保持一致。"""
    return datetime.now(DATABASE_TIMEZONE).replace(tzinfo=None)


def api_timestamp(value):
    """把数据库中的 Asia/Shanghai DATETIME 明确转换为 UTC ISO-8601。"""
    if value is None:
        return None
    if value.tzinfo is None:
        value = value.replace(tzinfo=DATABASE_TIMEZONE)
    value = value.astimezone(timezone.utc)
    return value.isoformat(timespec="milliseconds").replace("+00:00", "Z")


def configure_engine_time_zone(engine):
    """固定 MySQL 会话时区，避免 CURRENT_TIMESTAMP 与 Java 历史数据语义不一致。"""
    if engine.dialect.name != "mysql":
        return

    @event.listens_for(engine, "connect")
    def set_session_time_zone(dbapi_connection, _connection_record):
        cursor = dbapi_connection.cursor()
        try:
            cursor.execute("SET time_zone = '+08:00'")
        finally:
            cursor.close()


def identifier(size=64):
    """构造指定长度的标识符列类型，在 MySQL 中使用区分大小写的 ASCII 排序规则。"""
    return String(size).with_variant(VARCHAR(size, charset="ascii", collation="ascii_bin"), "mysql")


def base_columns():
    """为每张 AI 表创建独立的主键、版本号和创建及更新时间列。"""
    return [
        Column("id", pk_type, primary_key=True, autoincrement=True),
        Column("version", BigInteger, nullable=False, default=0),
        Column("create_time", DateTime, nullable=False, default=now),
        Column("update_time", DateTime, nullable=False, default=now, onupdate=now),
    ]


conversations = Table(
    "ai_conversation",
    metadata,
    *base_columns(),
    Column("conversation_id", identifier(), nullable=False, unique=True),
    Column("user_id", BigInteger, nullable=False, index=True),
    Column("channel", String(32), nullable=False, default="WEB"),
    Column("title", String(100)),
    Column("status", String(32), nullable=False, default="BOT_ACTIVE"),
    Column("current_handler", String(16), nullable=False, default="BOT"),
    Column("related_order_id", BigInteger),
    Column("last_message_sequence", BigInteger, nullable=False, default=0),
    Column("last_message_time", DateTime),
    Column("started_at", DateTime, nullable=False, default=now),
    Column("ended_at", DateTime),
    mysql_charset="utf8mb4",
    mysql_collate="utf8mb4_unicode_ci",
)
messages = Table(
    "ai_message",
    metadata,
    *base_columns(),
    Column("message_id", identifier(), nullable=False, unique=True),
    Column(
        "conversation_id",
        identifier(),
        ForeignKey("ai_conversation.conversation_id"),
        nullable=False,
    ),
    Column("sequence_no", BigInteger, nullable=False),
    Column("role", String(16), nullable=False),
    Column("content", Text().with_variant(MEDIUMTEXT(), "mysql"), nullable=False),
    Column("status", String(24), nullable=False, default="PENDING"),
    Column("client_request_id", identifier()),
    Column("request_fingerprint", identifier()),
    Column("reply_to_message_id", identifier(), ForeignKey("ai_message.message_id"), unique=True),
    Column("provider", String(32)),
    Column("model_name", String(100)),
    Column("input_tokens", Integer),
    Column("output_tokens", Integer),
    Column("latency_ms", Integer),
    Column("error_code", String(64)),
    Column("trace_id", identifier()),
    Column("processing_owner", identifier()),
    Column("lease_expires_at", DateTime, index=True),
    UniqueConstraint("conversation_id", "sequence_no"),
    UniqueConstraint("conversation_id", "client_request_id"),
    mysql_charset="utf8mb4",
    mysql_collate="utf8mb4_unicode_ci",
)
tool_calls = Table(
    "ai_tool_call",
    metadata,
    *base_columns(),
    Column("tool_call_id", identifier(), nullable=False, unique=True),
    Column(
        "conversation_id",
        identifier(),
        ForeignKey("ai_conversation.conversation_id"),
        nullable=False,
    ),
    Column("message_id", identifier(), ForeignKey("ai_message.message_id")),
    Column("tool_name", String(100), nullable=False),
    Column("parameter_summary", JSON),
    Column("result_status", String(24), nullable=False, default="STARTED"),
    Column("result_code", String(64)),
    Column("result_summary", String(1000)),
    Column("latency_ms", Integer),
    Column("idempotency_key", identifier(128), unique=True),
    Column("trace_id", identifier(), nullable=False),
    mysql_charset="utf8mb4",
    mysql_collate="utf8mb4_unicode_ci",
)


class Store:
    def __init__(self, url):
        """根据连接地址创建 SQLAlchemy 引擎，并在借出连接时检查其可用性。"""
        self.engine = create_engine(url, pool_pre_ping=True)
        configure_engine_time_zone(self.engine)

    def owned(self, conn, user, cid):
        """在现有连接中读取当前用户拥有的会话，返回字典；不存在或越权统一抛出 404。"""
        row = (
            conn.execute(
                select(conversations).where(
                    conversations.c.conversation_id == cid,
                    conversations.c.user_id == user,
                )
            )
            .mappings()
            .first()
        )
        if row is None:
            raise ApiError(404, "CONVERSATION_NOT_FOUND", "未找到该会话")
        return dict(row)

    def create(self, user, title):
        """在事务中为用户创建会话，并返回包含数据库默认值的完整会话记录。"""
        cid = uid()
        with self.engine.begin() as conn:
            conn.execute(
                insert(conversations).values(conversation_id=cid, user_id=user, title=title)
            )
            return self.owned(conn, user, cid)

    def get(self, user, cid):
        """打开连接并返回用户拥有的指定会话，不存在或越权时抛出 ApiError。"""
        with self.engine.connect() as conn:
            return self.owned(conn, user, cid)

    def list(self, user, limit, offset):
        """按最近更新时间分页读取用户会话，多取一条供调用方判断是否还有下一页。"""
        with self.engine.connect() as conn:
            return list(
                conn.execute(
                    select(conversations)
                    .where(conversations.c.user_id == user)
                    .order_by(conversations.c.update_time.desc(), conversations.c.id.desc())
                    .offset(offset)
                    .limit(limit + 1)
                ).mappings()
            )

    def history(self, user, cid, after=0, limit=100):
        """校验会话归属后按序号升序读取历史，返回 after 之后最多 limit + 1 条消息。"""
        with self.engine.connect() as conn:
            self.owned(conn, user, cid)
            return list(
                conn.execute(
                    select(messages)
                    .where(messages.c.conversation_id == cid, messages.c.sequence_no > after)
                    .order_by(messages.c.sequence_no)
                    .limit(limit + 1)
                ).mappings()
            )

    def context_messages(self, user, cid):
        """读取用户会话中最近 12 条已完成消息，并按时间顺序返回供模型使用。"""
        with self.engine.connect() as conn:
            self.owned(conn, user, cid)
            rows = conn.execute(
                select(messages)
                .where(messages.c.conversation_id == cid, messages.c.status == "COMPLETED")
                .order_by(messages.c.sequence_no.desc())
                .limit(12)
            ).mappings()
            return list(reversed(list(rows)))

    def _expire_stale_turns(self, conn, cid=None):
        """把租约已过期的处理中消息和工具审计原子地转为失败状态。"""
        conditions = [
            messages.c.role == "ASSISTANT",
            messages.c.status == "PENDING",
            messages.c.lease_expires_at.is_not(None),
            messages.c.lease_expires_at <= now(),
        ]
        if cid is not None:
            conditions.append(messages.c.conversation_id == cid)
        stale_ids = list(
            conn.execute(select(messages.c.message_id).where(*conditions)).scalars()
        )
        if not stale_ids:
            return 0
        conn.execute(
            update(tool_calls)
            .where(
                tool_calls.c.message_id.in_(stale_ids),
                tool_calls.c.result_status == "STARTED",
            )
            .values(
                result_status="FAILED",
                result_code="WORKER_LEASE_EXPIRED",
                version=tool_calls.c.version + 1,
            )
        )
        result = conn.execute(
            update(messages)
            .where(messages.c.message_id.in_(stale_ids), messages.c.status == "PENDING")
            .values(
                status="FAILED",
                error_code="WORKER_LEASE_EXPIRED",
                processing_owner=None,
                lease_expires_at=None,
                version=messages.c.version + 1,
            )
        )
        return result.rowcount

    def expire_stale_turns(self):
        """启动时清理全部过期租约；并发执行安全且不会触碰有效租约。"""
        with self.engine.begin() as conn:
            return self._expire_stale_turns(conn)

    def begin_turn(
        self,
        user,
        cid,
        text,
        request_id,
        provider,
        model,
        trace,
        owner=None,
        lease_seconds=300,
    ):
        """原子占用一轮聊天，返回用户消息、助手消息和是否为已完成请求回放。

        通过会话行锁、请求指纹和唯一约束控制幂等及并发；新请求分配连续序号。
        相同请求键正文冲突、上一轮未完成或会话不可自动回复时抛出 ApiError。
        """
        fingerprint = sha256(text.encode()).hexdigest()
        owner = owner or uid()
        with self.engine.begin() as conn:
            # Acquire a database row lock via UPDATE, including SQLite test databases.
            result = conn.execute(
                update(conversations)
                .where(
                    conversations.c.conversation_id == cid,
                    conversations.c.user_id == user,
                )
                .values(version=conversations.c.version + 1)
            )
            if result.rowcount != 1:
                raise ApiError(404, "CONVERSATION_NOT_FOUND", "未找到该会话")
            conv = self.owned(conn, user, cid)
            self._expire_stale_turns(conn, cid)
            existing = (
                conn.execute(
                    select(messages).where(
                        messages.c.conversation_id == cid,
                        messages.c.client_request_id == request_id,
                    )
                )
                .mappings()
                .first()
            )
            if existing:
                if existing["request_fingerprint"] != fingerprint:
                    raise ApiError(409, "IDEMPOTENCY_CONFLICT", "请求编号已用于不同消息")
                reply = (
                    conn.execute(
                        select(messages).where(
                            messages.c.reply_to_message_id == existing["message_id"]
                        )
                    )
                    .mappings()
                    .first()
                )
                if reply and reply["status"] == "COMPLETED":
                    return dict(existing), dict(reply), True
                if reply and reply["status"] == "FAILED":
                    raise ApiError(
                        503,
                        reply["error_code"] or "PREVIOUS_REQUEST_FAILED",
                        "上次请求失败，请使用新的请求编号重试",
                    )
                raise ApiError(409, "REQUEST_IN_PROGRESS", "该请求正在处理中")
            if conv["status"] != "BOT_ACTIVE" or conv["current_handler"] != "BOT":
                raise ApiError(409, "CONVERSATION_NOT_ACTIVE", "当前会话不能自动回复")
            pending = conn.execute(
                select(messages.c.id).where(
                    messages.c.conversation_id == cid,
                    messages.c.role == "ASSISTANT",
                    messages.c.status == "PENDING",
                )
            )
            if pending.first():
                raise ApiError(409, "REQUEST_IN_PROGRESS", "请等待上一条消息处理完成")
            user_id, assistant_id = uid(), uid()
            sequence = conv["last_message_sequence"]
            conn.execute(
                insert(messages).values(
                    message_id=user_id,
                    conversation_id=cid,
                    sequence_no=sequence + 1,
                    role="USER",
                    content=text,
                    status="COMPLETED",
                    client_request_id=request_id,
                    request_fingerprint=fingerprint,
                    trace_id=trace,
                )
            )
            conn.execute(
                insert(messages).values(
                    message_id=assistant_id,
                    conversation_id=cid,
                    sequence_no=sequence + 2,
                    role="ASSISTANT",
                    content="",
                    status="PENDING",
                    reply_to_message_id=user_id,
                    provider=provider,
                    model_name=model,
                    trace_id=trace,
                    processing_owner=owner,
                    lease_expires_at=now() + timedelta(seconds=lease_seconds),
                )
            )
            conn.execute(
                update(conversations)
                .where(conversations.c.conversation_id == cid)
                .values(last_message_sequence=sequence + 2, last_message_time=now())
            )
            rows = (
                conn.execute(
                    select(messages)
                    .where(messages.c.message_id.in_([user_id, assistant_id]))
                    .order_by(messages.c.sequence_no)
                )
                .mappings()
                .all()
            )
            return dict(rows[0]), dict(rows[1]), False

    def renew_lease(self, mid, owner, lease_seconds):
        """仅由当前处理者续租；消息已被恢复或接管时返回 False。"""
        with self.engine.begin() as conn:
            result = conn.execute(
                update(messages)
                .where(
                    messages.c.message_id == mid,
                    messages.c.status == "PENDING",
                    messages.c.processing_owner == owner,
                )
                .values(lease_expires_at=now() + timedelta(seconds=lease_seconds))
            )
            return result.rowcount == 1

    def finish(self, mid, answer, expected_version, owner, error=None, latency=0):
        """使用版本号和处理者双重 CAS 将助手消息更新为完成或失败。"""
        if error:
            status = "FAILED"
        else:
            status = "COMPLETED"
        with self.engine.begin() as conn:
            result = conn.execute(
                update(messages)
                .where(
                    messages.c.message_id == mid,
                    messages.c.status == "PENDING",
                    messages.c.version == expected_version,
                    messages.c.processing_owner == owner,
                )
                .values(
                    content=answer,
                    status=status,
                    error_code=error,
                    latency_ms=latency,
                    processing_owner=None,
                    lease_expires_at=None,
                    version=messages.c.version + 1,
                )
            )
            return result.rowcount == 1

    def link_order(self, user, cid, order_id):
        """在用户归属约束下更新会话订单关联；仅应在 Java 订单查询成功后调用。"""
        with self.engine.begin() as conn:
            conn.execute(
                update(conversations)
                .where(conversations.c.conversation_id == cid, conversations.c.user_id == user)
                .values(related_order_id=order_id)
            )

    def audit_start(self, cid, mid, name, summary, trace):
        """保存工具调用的脱敏参数摘要和追踪信息，返回新建审计记录的工具调用 ID。"""
        tid = uid()
        with self.engine.begin() as conn:
            conn.execute(
                insert(tool_calls).values(
                    tool_call_id=tid,
                    conversation_id=cid,
                    message_id=mid,
                    tool_name=name,
                    parameter_summary=summary,
                    trace_id=trace,
                )
            )
        return tid

    def audit_finish(self, tid, status, code, latency, expected_version=0):
        """以版本号和 STARTED 状态 CAS 更新工具审计终态。"""
        with self.engine.begin() as conn:
            result = conn.execute(
                update(tool_calls)
                .where(
                    tool_calls.c.tool_call_id == tid,
                    tool_calls.c.result_status == "STARTED",
                    tool_calls.c.version == expected_version,
                )
                .values(
                    result_status=status,
                    result_code=code,
                    latency_ms=latency,
                    version=tool_calls.c.version + 1,
                )
            )
            return result.rowcount == 1
