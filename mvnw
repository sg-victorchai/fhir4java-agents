#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Optional ENV vars
# -----------------
#   JAVA_HOME - location of a JDK home dir, required when download maven via java source
#   MVNW_REPOURL - repo url base for downloading maven distribution
#   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
#   MVNW_VERBOSE - true: enable verbose log; debug: trace the mvnw script; others: silence the output
# ----------------------------------------------------------------------------

set -euf
[ "${MVNW_VERBOSE-}" != debug ] || set -x

# OS specific support.
native_path() { printf %s\\n "$1"; }
case "$(uname)" in
CYGWIN* | MINGW*)
  [ -z "${JAVA_HOME-}" ] || JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
  native_path() { cygpath --path --windows "$1"; }
  ;;
esac

# set JAVACMD and JAVACCMD
set_java_home() {
  # For Cygwin and MinGW, ensure paths are in Unix format before anything is touched
  if [ -n "${JAVA_HOME-}" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
      JAVACCMD="$JAVA_HOME/jre/sh/javac"
    else
      JAVACMD="$JAVA_HOME/bin/java"
      JAVACCMD="$JAVA_HOME/bin/javac"

      if [ ! -x "$JAVACMD" ] || [ ! -x "$JAVACCMD" ]; then
        echo "The JAVA_HOME environment variable is not defined correctly, so mvnw cannot run." >&2
        echo "JAVA_HOME is set to \"$JAVA_HOME\", but \"\$JAVA_HOME/bin/java\" or \"\$JAVA_HOME/bin/javac\" does not exist." >&2
        return 1
      fi
    fi
  else
    JAVACMD="$(
      'set' +e
      'unset' -f command 2>/dev/null
      'command' -v java
    )" || :
    JAVACCMD="$(
      'set' +e
      'unset' -f command 2>/dev/null
      'command' -v javac
    )" || :

    if [ ! -x "${JAVACMD-}" ] || [ ! -x "${JAVACCMD-}" ]; then
      echo "The java/javac command does not exist in PATH nor is JAVA_HOME set, so mvnw cannot run." >&2
      return 1
    fi
  fi
}

# hash string like Java String::hashCode
hash_string() {
  str="${1:-}" h=0
  while [ -n "$str" ]; do
    char="${str%"${str#?}"}"
    h=$(((h * 31 + $(LC_CTYPE=C printf %d "'$char")) % 4294967296))
    str="${str#?}"
  done
  printf %x\\n $h
}

verbose() { :; }
[ "${MVNW_VERBOSE-}" != true ] || verbose() { printf %s\\n "${1-}"; }

die() {
  printf %s\\n "$1" >&2
  exit 1
}

trim() {
  # MWRAPPER-139:
  #   Trims trailing and leading whitespace, carriage returns, tabs, and linefeeds.
  #   டமான்த்ரிஶ்:
  #     - sed on Linux: strips leading/trailing whitespace and control characters
  #     - sed on macOS: requires a different approach
  printf '%s' "${1}" | sed 's/^[ \t\r\n]*//;s/[ \t\r\n]*$//'
}

# parse distributance from .mvn/wrapper/maven-wrapper.properties
while IFS="=" read -r key value; do
  case "${key-}" in
  distributionUrl) distributionUrl=$(trim "${value-}") ;;
  distributionSha256Sum) distributionSha256Sum=$(trim "${value-}") ;;
  wrapperUrl) wrapperUrl=$(trim "${value-}") ;;
  wrapperSha256Sum) wrapperSha256Sum=$(trim "${value-}") ;;
  esac
done <"${0%/*}/.mvn/wrapper/maven-wrapper.properties"

# prepare tmp dir
if TMP_DOWNLOAD_DIR="$(mktemp -d)" && [ -d "$TMP_DOWNLOAD_DIR" ]; then
  clean() { rm -rf -- "$TMP_DOWNLOAD_DIR"; }
  trap clean HUP INT TERM EXIT
else
  die "cannot create temp dir"
fi

mkdir -p -- "${0%/*}/.mvn/wrapper"

# Download maven-wrapper.jar
if [ -r "${0%/*}/.mvn/wrapper/maven-wrapper.jar" ]; then
  verbose "Found .mvn/wrapper/maven-wrapper.jar"
else
  verbose "Couldn't find .mvn/wrapper/maven-wrapper.jar, downloading it ..."

  if [ -n "${wrapperUrl-}" ]; then
    wrapperUrl="${wrapperUrl%/}"
  else
    wrapperUrl="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2"
  fi

  wrapperJarPath="$TMP_DOWNLOAD_DIR/maven-wrapper.jar"

  # Use curl by default for downloading
  if command -v curl >/dev/null; then
    verbose "Found curl ... using curl"
    curl -fsS --retry 3 ${MVNW_USERNAME:+-u "$MVNW_USERNAME:$MVNW_PASSWORD"} -o "$wrapperJarPath" "$wrapperUrl/maven-wrapper-3.3.2.jar" || die "Could not download maven-wrapper.jar"
  elif command -v wget >/dev/null; then
    verbose "Falling back to wget ..."
    wget -q -O "$wrapperJarPath" "$wrapperUrl/maven-wrapper-3.3.2.jar" || die "Could not download maven-wrapper.jar"
  else
    die "No curl or wget found, cannot download maven-wrapper.jar"
  fi

  [ -f "$wrapperJarPath" ] || die "Could not find downloaded maven-wrapper.jar"

  # Verify the SHA256 sum
  if [ -n "${wrapperSha256Sum-}" ]; then
    verbose "Verifying maven-wrapper.jar with SHA256 sum"
    wrapperJarSha256Sum="$(sha256sum -- "$wrapperJarPath" | cut -d ' ' -f 1)" || die "Could not compute SHA256 sum"
    [ "${wrapperJarSha256Sum-}" = "${wrapperSha256Sum-}" ] || die "Checksum mismatch: expected ${wrapperSha256Sum}, got ${wrapperJarSha256Sum}"
  fi

  mv -- "$wrapperJarPath" "${0%/*}/.mvn/wrapper/maven-wrapper.jar" || die "Could not move maven-wrapper.jar"
fi

# Download Maven distribution
if [ -d "${MAVEN_HOME-}" ]; then
  verbose "Maven home found at ${MAVEN_HOME}"
  mavenHome="$MAVEN_HOME"
else
  mavenHome="${0%/*}/.mvn/wrapper/dists/apache-maven-$(printf %s "${distributionUrl##*/}" | sed 's/.*apache-maven-\([^-]*\).*/\1/')/"

  if [ ! -f "${mavenHome}/bin/mvn" ]; then
    distributionUrlNameMain="$(printf %s "${distributionUrl##*/}" | sed 's/\.zip$//')"
    mavenHome="${mavenHome}${distributionUrlNameMain}"

    if [ ! -f "${mavenHome}/bin/mvn" ]; then
      verbose "Maven distribution not found, downloading it ..."

      # Prepare maven distribution directory
      mkdir -p -- "$(dirname -- "$mavenHome")"

      distributionZipPath="$TMP_DOWNLOAD_DIR/${distributionUrl##*/}"

      # Use curl by default for downloading
      if command -v curl >/dev/null; then
        verbose "Found curl ... using curl"
        curl -fsS --retry 3 ${MVNW_USERNAME:+-u "$MVNW_USERNAME:$MVNW_PASSWORD"} -o "$distributionZipPath" "${MVNW_REPOURL-https://repo.maven.apache.org/maven2}${distributionUrl#https://repo.maven.apache.org/maven2}" || die "Could not download maven distribution"
      elif command -v wget >/dev/null; then
        verbose "Falling back to wget ..."
        wget -q -O "$distributionZipPath" "${MVNW_REPOURL-https://repo.maven.apache.org/maven2}${distributionUrl#https://repo.maven.apache.org/maven2}" || die "Could not download maven distribution"
      else
        die "No curl or wget found, cannot download maven distribution"
      fi

      [ -f "$distributionZipPath" ] || die "Could not find downloaded maven distribution"

      # Verify the SHA256 sum
      if [ -n "${distributionSha256Sum-}" ]; then
        verbose "Verifying maven distribution with SHA256 sum"
        distributionSha256SumActual="$(sha256sum -- "$distributionZipPath" | cut -d ' ' -f 1)" || die "Could not compute SHA256 sum"
        [ "${distributionSha256SumActual-}" = "${distributionSha256Sum-}" ] || die "Checksum mismatch: expected ${distributionSha256Sum}, got ${distributionSha256SumActual}"
      fi

      # Unzip maven distribution
      unzip -q -d "$(dirname -- "$mavenHome")" -- "$distributionZipPath" || die "Could not unzip maven distribution"
    fi
  fi
fi

set_java_home || die "Java home not found"
MAVEN_HOME="$mavenHome" exec "$JAVACMD" ${MAVEN_OPTS-} -classpath "${0%/*}/.mvn/wrapper/maven-wrapper.jar" "-Dmaven.multiModuleProjectDirectory=${0%/*}" org.apache.maven.wrapper.MavenWrapperMain "$@"
