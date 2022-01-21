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

REM unzip built application
7z x ..\%MOQUI_HOME%\moqui-plus-runtime.war

REM copy source code
mkdir moqui-source
xcopy /E /i ..\%MOQUI_HOME%\runtime moqui-source\runtime
xcopy /E /i ..\%MOQUI_HOME%\framework moqui-source\framework
xcopy /E /i ..\%MOQUI_HOME%\moqui-util moqui-source\moqui-util
REM xcopy /E /i ..\%MOQUI_HOME%\gradle moqui-source\gradle
xcopy ..\%MOQUI_HOME%\*.* moqui-source

REM 1. run database (inside separate component)
docker run -d --rm --name dev-postgres -e POSTGRES_PASSWORD=postgres -e PGPORT=5431 -e POSTGRES_HOST_AUTH_METHOD=trust -p 5431:5431 --network=dtq-int postgres
cat ./CreateDatabase.sql | docker exec -i dev-postgres psql -U postgres -d postgres

REM 2. run application component (inside separate component as well) - this is the component that is being built


REM RUN IN ENTIRETY
REM set the project name to 'moqui', network will be called 'moqui_default'
docker-compose -f ../%COMP_FILE% -p moqui-dynamic --verbose up -d --build

REM docker stop dev-postgres

REM delete all that remains
rmdir /Q /S META-INF
rmdir /Q /S WEB-INF
rmdir /Q /S execlib
rmdir /Q /S runtime
del *.class
del Procfile

rmdir /Q /S moqui-source

REM return back to original dir
popd

pause