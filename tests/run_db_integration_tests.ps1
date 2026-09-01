param([string]$ProjectDir = ".")
$ErrorActionPreference = "Stop"

if ($env:GAS_ARTIFACT_TEST_DB_ACK -ne "YES") {
    throw "Refusing to run. Set GAS_ARTIFACT_TEST_DB_ACK=YES after selecting a disposable DB."
}
$Required = @(
    "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
    "SECURITY_SIGNATURE_SECRET", "INTEGRITY_CHAIN_SECRET"
)
foreach ($Name in $Required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Name))) {
        throw "Required environment variable is missing: $Name"
    }
}

Set-Location $ProjectDir
$env:RUN_DB_IT = "true"
$MavenArgs = @(
    "-Dmaven.test.skip=false",
    "-DskipTests=false",
    "-Dtest=CheckpointVerificationIT,IntegrityCheckpointAuditIT,TamperingDetectionIT",
    "test"
)

if (Test-Path ".\mvnw.cmd") {
    & ".\mvnw.cmd" @MavenArgs
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    & mvn @MavenArgs
} elseif (Test-Path ".\.mvn\wrapper\maven-wrapper.jar") {
    $Java = $null
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $Candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $Candidate) { $Java = $Candidate }
    }
    if ($null -eq $Java) {
        $JavaCommand = Get-Command java -ErrorAction SilentlyContinue
        if ($null -ne $JavaCommand) { $Java = $JavaCommand.Source }
    }
    if ($null -eq $Java) {
        throw "Java was not found. Set JAVA_HOME to JDK 17 or add java to PATH."
    }
    & $Java "-Dmaven.multiModuleProjectDirectory=$PWD" `
        -classpath ".\.mvn\wrapper\maven-wrapper.jar" `
        org.apache.maven.wrapper.MavenWrapperMain @MavenArgs
} else {
    throw "Maven was not found. Install Maven 3.8+ or restore the Maven Wrapper files."
}

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Surefire reports: $ProjectDir\target\surefire-reports"
