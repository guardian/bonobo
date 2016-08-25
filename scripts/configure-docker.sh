#! /bin/bash

echo Starting Services ...
docker-compose up -d

echo Adding API ...
wget -O - http://127.0.0.1:8001/apis --post-data 'name=internal&request_host=foo.com&upstream_url=http://example.com' --retry-connrefused --no-verbose

echo Activating key-auth plugin ...
wget -O - http://127.0.0.1:8001/apis/internal/plugins/ --post-data 'name=key-auth' --retry-connrefused --no-verbose
