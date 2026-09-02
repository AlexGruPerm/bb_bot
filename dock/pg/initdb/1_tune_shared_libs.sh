#!/bin/bash

set -e 

echo "=> Tune shared_preload_libraries and logs in postgresql.conf"
echo "==> PGDATA is: $PGDATA"

if [ ! -f "$PGDATA/postgresql.conf" ]; then
  echo "Error: postgresql.conf not found in $PGDATA" 
  exit 1
fi

if [ ! -w "$PGDATA/postgresql.conf" ]; then
  echo "Error: postgresql.conf is not writable"
  exit 1
fi

echo "====> parameters for logs"
echo "log_directory = '/var/lib/postgresql/logs'" >> "$PGDATA/postgresql.conf"
echo "log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log'" >> "$PGDATA/postgresql.conf"
echo "logging_collector = on" >> "$PGDATA/postgresql.conf"

echo "====> external HBA"
echo "hba_file = '/etc/postgresql/pg_hba.conf'" >> "$PGDATA/postgresql.conf"

echo "=====> Fixed postgresql.conf"


