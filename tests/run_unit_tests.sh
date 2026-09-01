#!/usr/bin/env bash
set -euo pipefail

project_dir="${1:-.}"
cd "$project_dir"

if [[ -x ./mvnw ]]; then
  mvn_cmd=(./mvnw)
elif command -v mvn >/dev/null 2>&1; then
  mvn_cmd=(mvn)
elif [[ -f ./.mvn/wrapper/maven-wrapper.jar ]]; then
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    java_cmd="$JAVA_HOME/bin/java"
  elif command -v java >/dev/null 2>&1; then
    java_cmd="$(command -v java)"
  else
    echo "Java was not found. Set JAVA_HOME to JDK 17 or add java to PATH." >&2
    exit 2
  fi
  mvn_cmd=("$java_cmd" "-Dmaven.multiModuleProjectDirectory=$PWD" -classpath ./.mvn/wrapper/maven-wrapper.jar org.apache.maven.wrapper.MavenWrapperMain)
else
  echo "Maven was not found. Install Maven 3.8+ or restore the Maven Wrapper files." >&2
  exit 2
fi

"${mvn_cmd[@]}" -Dmaven.test.skip=false -DskipTests=false \
  -Dtest=TrbacPerRequestFilterTest,IntegrityServiceTest,SecurityUtilTest test
echo "Surefire reports: $project_dir/target/surefire-reports"
