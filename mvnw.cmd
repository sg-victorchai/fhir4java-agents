@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSMODULEP_SAVE__=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0telerik.com'; $env:__MVNW_SCRIPT__=$scriptDir; Get-Content -Raw '%~dp0.mvn\wrapper\maven-wrapper.properties' | foreach-object { $_ -replace '(?<!\\)\\(?![\nrt])(?=[^n\r\\])', '\\' } | ConvertFrom-StringData | foreach-object { $_.GetEnumerator() | foreach-object { if ($_.Key -match 'Url') { $_.Key + '=' + ($_.Value -replace '{{scriptDir}}', $scriptDir) } else { $_.Key + '=' + $_.Value } } }}"`) DO @(
    IF "%%A"=="distributionUrl" SET "MVNW_DISTRIBUTIONURL=%%B"
    IF "%%A"=="wrapperUrl" SET "MVNW_WRAPPERURL=%%B"
)
@IF "%MVNW_VERBOSE%"=="true" @ECHO wrapper url = %MVNW_WRAPPERURL%
@IF "%MVNW_VERBOSE%"=="true" @ECHO distribution url = %MVNW_DISTRIBUTIONURL%

@REM Extension to allow automatically downloading the maven-wrapper.jar from Maven-Central
@IF NOT EXIST "%~dp0.mvn\wrapper\maven-wrapper.jar" (
    @ECHO Maven wrapper jar not found, downloading it ...
    @FOR /F "tokens=1,2 delims==" %%A IN ('powershell -noprofile -command "$p=New-Object System.Net.WebClient; $p.Headers.Add('User-Agent', 'Maven Wrapper/3.3.2 (Windows)'); $p.DownloadFile('%MVNW_WRAPPERURL%/maven-wrapper-3.3.2.jar', '.mvn\wrapper\maven-wrapper.jar')" 2^>^&1`) DO @(
        @IF "%%A"=="Exception" @SET __MVNW_ERROR__=%%B
    )
    @IF DEFINED __MVNW_ERROR__ (
        @ECHO Error downloading maven wrapper: %__MVNW_ERROR__%
        @ECHO Trying alternative method ...
        @powershell -noprofile -command "& { Invoke-WebRequest -Uri '%MVNW_WRAPPERURL%/maven-wrapper-3.3.2.jar' -OutFile '.mvn\wrapper\maven-wrapper.jar' }"
    )
)

@SET MVNW_JAVA_EXE=java.exe
@IF NOT "%JAVA_HOME%"=="" @SET MVNW_JAVA_EXE=%JAVA_HOME%\bin\java.exe

@IF "%MVNW_VERBOSE%"=="true" @ECHO java = %MVNW_JAVA_EXE%

@FOR /F "tokens=*" %%A IN ('"%MVNW_JAVA_EXE%" -version 2^>^&1') DO @(
    @IF "%MVNW_VERBOSE%"=="true" @ECHO %%A
)

@SET PSModulePath=%__MVNW_PSMODULEP_SAVE__%

"%MVNW_JAVA_EXE%" %MAVEN_OPTS% -classpath "%~dp0.mvn\wrapper\maven-wrapper.jar" "-Dmaven.multiModuleProjectDirectory=%~dp0" org.apache.maven.wrapper.MavenWrapperMain %*
@IF ERRORLEVEL 1 EXIT /B %ERRORLEVEL%
