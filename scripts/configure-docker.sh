#! /bin/bash

kong_url=${1?Input file parameter missing}

docker create -p 9042:9042 --name cassandra mashape/cassandra

docker create -p 8000:8000 -p 8001:8001 --name kong --link cassandra:cassandra mashape/kong:0.5.3

docker start cassandra

docker start kong

sleep 5

curl -sS -X POST ${kong_url}/apis -d name=internal -d request_host=foo.com -d upstream_url=http://example.com

curl -sS --url ${kong_url}/apis/internal/plugins/ -d name=key-auth