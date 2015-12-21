#! /bin/bash

docker_host=${DOCKER_HOST?Docker host is not set}

container_host=$(echo ${docker_host} | sed -e 's/tcp:\/\/\(.*\):.*/\1/')

function start_service {
    name=${1?Name parameter missing}
    port_number=${2?Port parameter missing}

    docker start ${name}

    nc -z ${container_host} ${port_number}
    while [ $? -ne 0 ]; do
        echo Waiting for ${name} to start listening ...
        sleep 1
        nc -z ${container_host} ${port_number}
    done
}

if docker ps | grep cassandra -q; then
    echo Cassandra container already exists
else
    echo Creating cassandra container ...
    docker create -p 9042:9042 --name cassandra mashape/cassandra
fi


if docker ps | grep kong -q; then
    echo Kong container already exists
else
    echo Creating kong container ...
    docker create -p 8000:8000 -p 8001:8001 --name kong --link cassandra:cassandra mashape/kong:0.5.3
fi

start_service cassandra 9042

start_service kong 8001

echo Adding API ...
curl -sS -X POST http://${container_host}:8001/apis -d name=internal -d request_host=foo.com -d upstream_url=http://example.com

echo Activating key-auth plugin ...
curl -sS -X POST http://${container_host}:8001/apis/internal/plugins/ -d name=key-auth

