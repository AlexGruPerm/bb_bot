#!/bin/sh

echo "Get java version:"
java --version

echo "Is service up..."
ping -c 4 postgres_db || exit 1

echo "Run application..."
exec java -jar /app/gather.jar control.conf
