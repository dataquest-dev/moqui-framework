ECHO OFF
ECHO "composing Moqui environment - no DB and no ELASTIC"

SET COMP_FILE=%~1
SET MOQUI_HOME=%~2

if [%1] == [] (
    ECHO "setting compose-file to default"
    SET COMP_FILE="moqui-pg-jdk-11-compose.yml"
)

if [%2] == [] (
    ECHO "setting path to default"
    SET MOQUI_HOME=".."
)

REM get into the simple-build directory
pushd ..\simple

REM 1. create database server (inside separate component) and fill it
docker-compose -p pg-dump   -f create-database.yml build --no-cache
docker-compose -p pg-dump   -f create-database.yml up -d

REM 2. run application component (inside separate component as well) - this is the component that is being built
REM this will actually initialize the database

REM fill database
docker-compose ^
    -p moqui-fill ^
    -f fill-database.yml ^
    build ^
    --no-cache

cat ./DumpDatabase.sql | docker exec -i dev-postgres su postgres

REM stop temp database


REM RUN IN ENTIRETY
REM set the project name to 'moqui', network will be called 'moqui_default'
REM docker-compose -f ../%COMP_FILE% -p moqui-dynamic --verbose up -d --build


docker-compose -p moqui-fill -f fill-database.yml down --rmi local -v
docker-compose -p pg-dump -f create-database.yml down --rmi local -v

REM return back to original dir
popd

pause