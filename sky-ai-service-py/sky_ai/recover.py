"""Offline recovery after stopping all AI workers; never re-executes model/tools."""

from sqlalchemy import select, update

from sky_ai.config import Settings
from sky_ai.database import Store, messages, tool_calls


def recover_pending(store):
    """在所有 AI 进程停止后，将中断的助手消息及未完成工具审计标记失败，返回消息数。

    该操作只修复持久化状态，不会重新调用模型或业务工具。
    """
    with store.engine.begin() as conn:
        pending = select(messages.c.message_id).where(
            messages.c.role == "ASSISTANT", messages.c.status == "PENDING"
        )
        conn.execute(
            update(tool_calls)
            .where(tool_calls.c.message_id.in_(pending), tool_calls.c.result_status == "STARTED")
            .values(
                result_status="FAILED",
                result_code="WORKER_INTERRUPTED",
                version=tool_calls.c.version + 1,
            )
        )
        result = conn.execute(
            update(messages)
            .where(messages.c.role == "ASSISTANT", messages.c.status == "PENDING")
            .values(
                status="FAILED",
                error_code="WORKER_INTERRUPTED",
                processing_owner=None,
                lease_expires_at=None,
                version=messages.c.version + 1,
            )
        )
        return result.rowcount


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(
        description="Mark interrupted turns failed after stopping all AI workers"
    )
    parser.add_argument("--workers-stopped", action="store_true", required=True)
    parser.parse_args()
    store = Store(Settings().sqlalchemy_url())
    try:
        print(f"Recovered {recover_pending(store)} interrupted turns")
    finally:
        store.engine.dispose()
