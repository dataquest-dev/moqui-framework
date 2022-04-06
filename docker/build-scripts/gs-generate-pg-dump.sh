#! /bin/bash

# "Creating database dump for purpose of having a clear and filled database"
# get into the simple-build directory
# shellcheck disable=SC2164
pushd ../gradle-tasks/fill-database

# 1. create database server (inside separate component) and fill it
docker-compose -p pg-dump   -f create-database.yml build --no-cache
docker-compose -p pg-dump   -f create-database.yml up -d

# 2. run application component (inside separate component as well) - this is the component that is being built
# this will actually initialize the database, without running the component
docker-compose -p moqui-fill -f fill-database.yml build --no-cache

# run DUMP script
type DumpDatabase.sql | docker exec -i dev-postgres su postgres

# kill containers
docker-compose -p moqui-fill -f fill-database.yml down --rmi local -v
docker-compose -p pg-dump -f create-database.yml down --rmi local -v

# return back to original dir
popd
