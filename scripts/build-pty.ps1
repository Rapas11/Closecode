# Builds the arm64 PTY bridge with the installed NDK and drops it in jniLibs.
# The app ships only arm64-v8a, so one target is the whole job.
$ErrorActionPreference = "Stop"

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$ndkRoot = Join-Path $sdk "ndk"
$ndk = Get-ChildItem $ndkRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1
if (-not $ndk) { throw "No NDK under $ndkRoot" }

$clang = Join-Path $ndk.FullName "toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android29-clang.cmd"
if (-not (Test-Path $clang)) { throw "NDK clang not found: $clang" }

$source = Join-Path $PSScriptRoot "..\app\src\main\cpp\closepty.c"
$outDir = Join-Path $PSScriptRoot "..\app\src\main\jniLibs\arm64-v8a"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$out = Join-Path $outDir "libclosepty.so"

& $clang -shared -fPIC -O2 -Wall -Wextra -Werror `
    "-Wl,-z,max-page-size=16384" `
    -o $out $source

if ($LASTEXITCODE -ne 0) { throw "clang failed with $LASTEXITCODE" }
Write-Host "Built $out ($((Get-Item $out).Length) bytes) with $($ndk.Name)"
