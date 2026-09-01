#!/usr/bin/env bash
set -euo pipefail

project_dir="${1:-.}"
if [[ "${GAS_ARTIFACT_TEST_DB_ACK:-}" != "YES" ]]; then
  echo "Refusing to run: set GAS_ARTIFACT_TEST_DB_ACK=YES after selecting a disposable DB." >&2
  exit 2
fi
for name in SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD SECURITY_SIGNATURE_SECRET INTEGRITY_CHAIN_SECRET; do
  if [[ -z "${!name:-}" ]]; then
    echo "Required environment variable is missing: $name" >&2
    exit 2
  fi
done

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

export RUN_DB_IT=true
"${mvn_cmd[@]}" -Dmaven.test.skip=false -DskipTests=false \
  -Dtest=CheckpointVerificationIT,IntegrityCheckpointAuditIT,TamperingDetectionIT test
echo "Surefire reports: $project_dir/target/surefire-reports"
