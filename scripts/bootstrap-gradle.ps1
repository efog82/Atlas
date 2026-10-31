$ErrorActionPreference = "Stop"
$version = "8.11.1"
$cache = Join-Path $env:USERPROFILE ".gradle\atlas-bootstrap"
$destination = Join-Path $cache "gradle-$version"
if (-not (Test-Path "$destination\bin\gradle.bat")) {
    New-Item -ItemType Directory -Path $cache -Force | Out-Null
    $url = "https://downloads.gradle.org/distributions/gradle-$version-bin.zip"
    $archive = Join-Path $cache "gradle.zip"
    $expected = (Invoke-WebRequest "$url.sha256").Content.Trim()
    Invoke-WebRequest $url -OutFile $archive
    if ((Get-FileHash $archive -Algorithm SHA256).Hash.ToLower() -ne $expected.ToLower()) { throw "Checksum inválido" }
    Expand-Archive $archive -DestinationPath $cache -Force
    Remove-Item $archive
}
Write-Host "Gradle: $destination\bin\gradle.bat"
Write-Host "Adiciona esta pasta ao PATH ou usa o caminho completo. No Android Studio seleciona esta instalação Gradle local."
