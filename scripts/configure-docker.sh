#! /bin/bash -e

echo Starting Kong Docker images ...
docker-compose up -d

echo Waiting for Kong
wget -O - http://localhost:8001/apis --retry-connrefused --no-verbose
echo Kong looks responsive

echo Adding API ...
curl -i -X POST \
  --url http://localhost:8001/apis/ \
  --data 'name=internal' \
  --data 'hosts=foo.com' \
  --data 'upstream_url=http://example.com'

echo Activating key-auth plugin ...
curl -i -X POST \
  --url http://localhost:8001/apis/internal/plugins/ \
  --data 'name=key-auth'

