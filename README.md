# bonobo

[![Circle CI](https://circleci.com/gh/guardian/bonobo/tree/master.svg?style=svg)](https://circleci.com/gh/guardian/bonobo/tree/master)

Key management for Kong

## Prerequisites for development

Bonobo needs an instance of Kong to connect to, so you will need Kong and Cassandra running somewhere. We recommend running them in Docker containers. See the "To run the integration tests" section below for instructions on setting this up.

## To run locally

Edit `conf/application.conf` to point `kong.apiAddress` at your Kong cluster.

Then start the Play app:

```
$ sbt run
```

It should now be running at http://localhost:9000/

You will need a Guardian Google account in order to login.

## To run the integration tests

The integration tests rely on Docker to run Kong. Install Docker Toolkit, following the instructions on the [Docker website](http://docs.docker.com/).

Make sure your Docker VM is running:

```
$ docker-machine ls
NAME   ACTIVE   DRIVER       STATE     URL                         SWARM
dev    *        virtualbox   Running   tcp://192.168.99.100:2376
```

Make sure `$DOCKER_HOST` is set:

```
$ echo $DOCKER_HOST
tcp://192.168.99.100:2376
```

If it is not, type `docker-machine env <machine-name>` to find out how to set it:

```
$ docker-machine env dev
export DOCKER_TLS_VERIFY="1"
export DOCKER_HOST="tcp://192.168.99.100:2376"
export DOCKER_CERT_PATH="/Users/cbirchall/.docker/machine/machines/dev"
export DOCKER_MACHINE_NAME="dev"
# Run this command to configure your shell:
# eval "$(docker-machine env dev)"
```

Now you can run the integration tests:

```
$ sbt it:test
```
