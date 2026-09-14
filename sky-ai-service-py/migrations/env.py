from alembic import context
from sqlalchemy import create_engine

from sky_ai.config import Settings
from sky_ai.database import configure_engine_time_zone, metadata

engine = create_engine(Settings().sqlalchemy_url())
configure_engine_time_zone(engine)
with engine.connect() as connection:
    context.configure(connection=connection, target_metadata=metadata)
    with context.begin_transaction():
        context.run_migrations()
