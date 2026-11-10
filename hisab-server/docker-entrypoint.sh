#!/bin/sh
set -eu

umask 027
install -d -m 0750 -o 10001 -g 10001 "$UPLOAD_DIR"
exec setpriv --reuid=10001 --regid=10001 --clear-groups --no-new-privs /opt/hisab/bin/hisab-server "$@"
