$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Out = Join-Path $Root "dist"
$Classes = Join-Path $Out "classes"
$Staging = Join-Path $Out "staging"

if (Test-Path $Out) { Remove-Item -LiteralPath $Out -Recurse -Force }
New-Item -ItemType Directory -Path $Classes | Out-Null
New-Item -ItemType Directory -Path $Staging | Out-Null

$Sources = Get-ChildItem -Path (Join-Path $Root "src") -Filter *.java -Recurse
javac --release 17 --add-modules jdk.httpserver -encoding UTF-8 -d $Classes $Sources.FullName
Copy-Item -Path (Join-Path $Root "resources\*") -Destination $Classes -Recurse
jar --create --file (Join-Path $Staging "ChinesePinyinIME-PC-Manager.jar") --main-class com.mercury.cime.manager.PcDictionaryManager -C $Classes .
jpackage --type app-image --name "ChinesePinyinIME词库管理" --input $Staging --main-jar "ChinesePinyinIME-PC-Manager.jar" --main-class com.mercury.cime.manager.PcDictionaryManager --add-modules java.desktop,jdk.httpserver --dest (Join-Path $Out "package")

Write-Host "Built: $Out\package\ChinesePinyinIME词库管理\ChinesePinyinIME词库管理.exe"
