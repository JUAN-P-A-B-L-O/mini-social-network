#!/bin/sh
set -eu
psql --username "$POSTGRES_USER" --dbname postgres --set=ON_ERROR_STOP=1 \
  --set=posts_password="$POSTS_DB_PASSWORD" --set=media_password="$MEDIA_DB_PASSWORD" <<'SQL'
CREATE ROLE posts_user LOGIN PASSWORD :'posts_password';
CREATE ROLE media_user LOGIN PASSWORD :'media_password';
CREATE DATABASE posts_db OWNER posts_user;
CREATE DATABASE media_db OWNER media_user;
REVOKE ALL ON DATABASE posts_db FROM PUBLIC;
REVOKE ALL ON DATABASE media_db FROM PUBLIC;
REVOKE ALL ON DATABASE postgres FROM PUBLIC;
REVOKE ALL ON DATABASE template1 FROM PUBLIC;
\connect posts_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO posts_user;
\connect media_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO media_user;
SQL
