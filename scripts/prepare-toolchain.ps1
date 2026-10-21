# prepare-toolchain.ps1 — run on the developer's PC. Produces the on-phone build-tools pack.
#
# What it does, in order:
#   1. downloads the original files from Maven Central and Google Maven,
#   2. converts the Java libraries to dex with D8 (desktop build-tools),
#   3. copies android.jar for API 35 out of the installed Android SDK,
#   4. extracts arm64 aapt2 into app/src/main/jniLibs/arm64-v8a (ships inside the APK),
#   5. resolves the supported AndroidX libraries with Gradle, extracts every AAR's
#      classes.jar, res folder and manifest, and packs them into libraries.zip,
#   6. writes app/src/main/assets/toolchain.json with id, version, address, size,
#      SHA-256 and license for every downloaded file.
#
# Outputs:
#   build-toolchain/out/<file>        — upload these files to the host (see -BaseUrl)
#   app/src/main/assets/toolchain.json — the manifest the app ships
#   app/src/main/jniLibs/arm64-v8a/libaapt2.so — the native resource compiler
#
# Anyone can repeat this: all inputs are public Maven artifacts and one GitHub release.
# The Kotlin compiler version must match ProjectPins.KOTLIN_VERSION in the app.

param(
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$JdkHome = "$env:LOCALAPPDATA\Jdks\jdk-17",
    # The host every file will be downloaded from on the phone. One place to swap.
    [string]$BaseUrl = "https://github.com/Rapas11/Closecode/releases/download/tools-v1/",
    [switch]$SkipDownload
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$work = Join-Path $repo 'build-toolchain'
$downloads = Join-Path $work 'downloads'
$out = Join-Path $work 'out'
$dexWork = Join-Path $work 'dex'
$jniLibs = Join-Path $repo 'app/src/main/jniLibs/arm64-v8a'
$assets = Join-Path $repo 'app/src/main/assets'

New-Item -ItemType Directory -Force -Path $downloads, $out, $dexWork, $jniLibs, $assets | Out-Null

$D8 = Join-Path $SdkRoot 'build-tools/35.0.0/lib/d8.jar'
$ANDROID_JAR = Join-Path $SdkRoot 'platforms/android-35/android.jar'
$JAVA = Join-Path $JdkHome 'bin/java.exe'
$JAR = Join-Path $JdkHome 'bin/jar.exe'

function Get-Sha256($path) {
    (Get-FileHash -Algorithm SHA256 -Path $path).Hash.ToLowerInvariant()
}

foreach ($needed in @($D8, $ANDROID_JAR, $JAVA, $JAR)) {
    if (-not (Test-Path $needed)) { throw "Missing on this PC: $needed" }
}

# ---------------------------------------------------------------------------------------
# The originals. versions match ProjectPins (Kotlin 1.9.24, Compose compiler 1.5.14).
# ---------------------------------------------------------------------------------------

$Maven = 'https://repo.maven.apache.org/maven2'
$Google = 'https://dl.google.com/dl/android/maven2'

# id, version, url, license, dexd? (true = phone runs it, so it must be dex)
$files = @(
    @{ id = 'kotlin-compiler'; version = '1.9.24'; license = 'Apache-2.0'; dex = $true
       url = "$Maven/org/jetbrains/kotlin/kotlin-compiler-embeddable/1.9.24/kotlin-compiler-embeddable-1.9.24.jar" }
    @{ id = 'kotlin-stdlib'; version = '1.9.24'; license = 'Apache-2.0'; dex = $false
       url = "$Maven/org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.jar" }
    @{ id = 'kotlin-reflect'; version = '1.9.24'; license = 'Apache-2.0'; dex = $true
       url = "$Maven/org/jetbrains/kotlin/kotlin-reflect/1.9.24/kotlin-reflect-1.9.24.jar" }
    @{ id = 'kotlin-script-runtime'; version = '1.9.24'; license = 'Apache-2.0'; dex = $true
       url = "$Maven/org/jetbrains/kotlin/kotlin-script-runtime/1.9.24/kotlin-script-runtime-1.9.24.jar" }
    @{ id = 'kotlin-daemon-embeddable'; version = '1.9.24'; license = 'Apache-2.0'; dex = $true
       url = "$Maven/org/jetbrains/kotlin/kotlin-daemon-embeddable/1.9.24/kotlin-daemon-embeddable-1.9.24.jar" }
    @{ id = 'trove4j'; version = '1.0.20200330'; license = 'LGPL-2.1'; dex = $true
       url = "$Maven/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar" }
    @{ id = 'annotations'; version = '13.0'; license = 'Apache-2.0'; dex = $true
       url = "$Maven/org/jetbrains/annotations/13.0/annotations-13.0.jar" }
    @{ id = 'coroutines'; version = '1.9.0'; license = 'Apache-2.0'; dex = $true
       url = "$Maven/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.9.0/kotlinx-coroutines-core-jvm-1.9.0.jar" }
    @{ id = 'ecj'; version = '3.46.0'; license = 'EPL-2.0'; dex = $true
       url = "$Maven/org/eclipse/jdt/ecj/3.46.0/ecj-3.46.0.jar" }
    @{ id = 'r8'; version = '8.13.25'; license = 'BSD-3-Clause'; dex = $true
       url = "$Google/com/android/tools/r8/8.13.25/r8-8.13.25.jar" }
)

# The Compose compiler plugin is downloaded but never shipped as its own file: its classes
# are merged into the Kotlin compiler dex so one classloader serves both. Kotlin 1.9.24
# pairs with Compose compiler 1.5.14, which lives on Google Maven — the Kotlin coordinate
# (kotlin-compose-compiler-plugin-embeddable) only starts at 2.0.
$extra = @(
    @{ id = 'compose-compiler'; version = '1.5.14'; license = 'Apache-2.0'
       url = "$Google/androidx/compose/compiler/compiler/1.5.14/compiler-1.5.14.jar" }
)

function Get-Original($file) {
    $name = [System.IO.Path]::GetFileName([Uri]$file.url)
    $target = Join-Path $downloads $name
    if ((Test-Path $target) -and (Get-Item $target).Length -gt 0) {
        Write-Host "have   $name"
        return $target
    }
    Write-Host "fetch  $name"
    # curl.exe ships with Windows and resumes half files; Invoke-WebRequest buffers.
    $curl = (Get-Command curl.exe -ErrorAction SilentlyContinue)
    if ($null -ne $curl) {
        & curl.exe -L --fail --silent --show-error -C - -o $target $file.url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $($file.url) (curl exit $LASTEXITCODE)" }
    } else {
        Invoke-WebRequest -Uri $file.url -OutFile $target -UseBasicParsing
    }
    if ((Get-Item $target).Length -eq 0) { throw "Empty download: $name" }
    return $target
}

# The Kotlin compiler and the Compose plugin live in ONE classloader on the phone, so they
# are dexed together. Everything else is dexed on its own.
$kotlinSet = @('kotlin-compiler', 'kotlin-stdlib', 'kotlin-reflect', 'kotlin-script-runtime',
               'kotlin-daemon-embeddable', 'trove4j', 'annotations', 'coroutines', 'compose-compiler')

function Invoke-D8($inputs, $outFile, $servicesFrom = $null) {
    $stage = Join-Path $dexWork ([System.IO.Path]::GetFileNameWithoutExtension($outFile))
    if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    $d8args = @('-cp', $D8, 'com.android.tools.r8.D8', '--min-api', '29',
              '--lib', $ANDROID_JAR, '--output', $stage) + $inputs
    & $JAVA @d8args
    if ($LASTEXITCODE -ne 0) { throw "D8 failed for $outFile (exit $LASTEXITCODE)" }
    $dexes = Get-ChildItem $stage -Filter '*.dex' | Sort-Object Name
    if ($dexes.Count -eq 0) { throw "D8 produced no dex for $outFile" }
    # Service files name the plugin classes; the dex keeps the classes, the zip keeps the
    # resources. Together they let the compiler's own DexClassLoader find the Compose plugin.
    if ($null -ne $servicesFrom) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $source = [System.IO.Compression.ZipFile]::OpenRead($servicesFrom)
        try {
            foreach ($entry in $source.Entries) {
                if ($entry.FullName -notlike 'META-INF/services/*' -or $entry.Name -eq '') { continue }
                $target = Join-Path $stage ($entry.FullName -replace '/', '\')
                New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
            }
        } finally {
            $source.Dispose()
        }
    }
    if (Test-Path $outFile) { Remove-Item $outFile -Force }
    # A dexed jar: plain zip with classes.dex, classes2.dex ... — what DexClassLoader eats.
    & $JAR --create --file $outFile -C $stage .
    if ($LASTEXITCODE -ne 0) { throw "jar failed for $outFile" }
    return $dexes.Count
}

Write-Host ''
Write-Host '1/5 downloading originals'
$originals = @{}
foreach ($file in @($files + $extra)) { $originals[$file.id] = Get-Original $file }

Write-Host ''
Write-Host '2/5 dexing for the phone'
$produced = @{}
foreach ($file in $files) {
    if (-not $file.dex) { continue }
    $dexName = "$($file.id)-$($file.version)-dex.jar"
    $dexPath = Join-Path $out $dexName
    if ($SkipDownload -and (Test-Path $dexPath) -and ((Get-Item $dexPath).Length -gt 0)) {
        Write-Host "have   $dexName"
    } elseif ($file.id -eq 'kotlin-compiler') {
        # one classloader: compiler + stdlib + plugin
        $set = $kotlinSet | ForEach-Object { $originals[$_] }
        $count = Invoke-D8 $set $dexPath $originals['compose-compiler']
        Write-Host "dexed  $dexName ($count dex files, compiler + compose plugin together)"
    } else {
        $count = Invoke-D8 @($originals[$file.id]) $dexPath
        Write-Host "dexed  $dexName ($count dex files)"
    }
    $produced[$file.id] = $dexName
}

Write-Host ''
Write-Host '3/5 android.jar and aapt2'
$androidOut = Join-Path $out 'android-35.jar'
Copy-Item $ANDROID_JAR $androidOut -Force
Write-Host "copied android-35.jar"

# arm64 aapt2: the community build the spec names (AndroidIDEOfficial/androidide-tools,
# build-tools 34.0.4, GPL-3.0 repository; aapt2 itself is AOSP, Apache-2.0).
$aapt2Tar = Join-Path $downloads 'build-tools-34.0.4-aarch64.tar.xz'
if (-not (Test-Path $aapt2Tar) -or (Get-Item $aapt2Tar).Length -eq 0) {
    Write-Host 'fetch  build-tools-34.0.4-aarch64.tar.xz'
    & curl.exe -L --fail --silent --show-error -C - -o $aapt2Tar 'https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/v34.0.4/build-tools-34.0.4-aarch64.tar.xz'
    if ($LASTEXITCODE -ne 0) { throw "aapt2 download failed (curl exit $LASTEXITCODE)" }
}
$aapt2Stage = Join-Path $work 'aapt2'
if (Test-Path $aapt2Stage) { Remove-Item $aapt2Stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $aapt2Stage | Out-Null
& tar -xf $aapt2Tar -C $aapt2Stage
if ($LASTEXITCODE -ne 0) { throw 'tar failed to extract the aapt2 archive' }
$aapt2 = Get-ChildItem $aapt2Stage -Recurse -Filter 'aapt2*' |
    Where-Object { -not $_.PSIsContainer -and $_.Extension -notin @('.exe', '.dll') } |
    Select-Object -First 1
if ($null -eq $aapt2) { throw 'aapt2 not found in the archive' }
Copy-Item $aapt2.FullName (Join-Path $jniLibs 'libaapt2.so') -Force
Write-Host ("placed libaapt2.so from " + $aapt2.Name)

Write-Host ''
Write-Host '4/5 libraries'
# The supported libraries are resolved by Gradle at pack time — the phone never runs
# Gradle. Each AAR's classes.jar, res folder and manifest are stored separately, which
# is exactly what the on-device build needs for aapt2, kotlinc, ECJ and D8.
$resolveDir = Join-Path $work 'library-pack'
$resolveOut = Join-Path $resolveDir 'out'
if (-not (Test-Path (Join-Path $resolveOut 'modules.tsv'))) {
    Write-Host 'resolving the supported libraries with Gradle'
    & (Join-Path $repo 'gradlew.bat') -p $resolveDir copyLibraries --console=plain
    if ($LASTEXITCODE -ne 0) { throw "library resolution failed (Gradle exit $LASTEXITCODE)" }
}
if (-not (Test-Path (Join-Path $resolveOut 'modules.tsv'))) {
    throw 'Gradle produced no modules.tsv; the library pack cannot be built'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$libStage = Join-Path $work 'libraries'
if (Test-Path $libStage) { Remove-Item $libStage -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $libStage 'jars'),
    (Join-Path $libStage 'res'), (Join-Path $libStage 'manifests') | Out-Null

$modules = New-Object System.Collections.Generic.List[object]
$seen = @{}
$number = 0
foreach ($line in Get-Content (Join-Path $resolveOut 'modules.tsv')) {
    if (-not $line.Trim()) { continue }
    $cols = $line -split "`t"
    if ($cols.Count -lt 3) { continue }
    $coords = $cols[0] -split ':'
    if ($coords.Count -lt 3) { continue }
    $module = "$($coords[0]):$($coords[1])"
    $version = $coords[2]
    # The toolchain ships one Kotlin stdlib (1.9.24); a second copy in the app would both
    # conflict with the compiler and duplicate every stdlib class in the dex.
    if ($module -eq 'org.jetbrains.kotlin:kotlin-stdlib') { continue }
    if ($seen.ContainsKey($module)) { continue }
    $seen[$module] = $true
    $number++
    $n = '{0:D3}' -f $number
    $source = Join-Path $resolveOut $cols[2]
    if (-not (Test-Path $source)) { throw "resolved library missing: $source" }
    $info = [ordered]@{ module = $module; version = $version }
    if ($source -like '*.aar') {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($source)
        try {
            foreach ($entry in $archive.Entries) {
                $name = $entry.FullName -replace '\\', '/'
                if ($name.EndsWith('/')) { continue }
                if ($name -eq 'classes.jar') {
                    $target = Join-Path $libStage "jars/$n.jar"
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
                    $info.jar = "jars/$n.jar"
                } elseif ($name -eq 'AndroidManifest.xml') {
                    $target = Join-Path $libStage "manifests/$n.xml"
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
                    $info.manifest = "manifests/$n.xml"
                } elseif ($name.StartsWith('res/')) {
                    $relative = $name.Substring(4)
                    if (-not $relative) { continue }
                    $target = Join-Path $libStage "res/$n/$relative"
                    $parent = Split-Path $target -Parent
                    New-Item -ItemType Directory -Force -Path $parent | Out-Null
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
                    $info.res = "res/$n"
                }
            }
        } finally {
            $archive.Dispose()
        }
    } else {
        Copy-Item $source (Join-Path $libStage "jars/$n.jar") -Force
        $info.jar = "jars/$n.jar"
    }
    $modules.Add($info)
}

# The libraries are dexed ONCE, here. On the phone, dexing every library on every build
# would take minutes and hundreds of megabytes; these classes never change between builds.
# The app dexes only its own classes and appends these as classes2.dex, classes3.dex...
# --min-api 24 is the lowest Android the build supports, so the dex runs on every target.
$libDex = Join-Path $work 'libdex'
$dexMarker = Join-Path $libDex 'inputs.txt'
$stdlibOriginal = Join-Path $downloads 'kotlin-stdlib-1.9.24.jar'
$dexInputs = Get-ChildItem (Join-Path $libStage 'jars') -Filter *.jar |
    Sort-Object Name | ForEach-Object { $_.Name + ':' + $_.Length }
$dexSignature = ($dexInputs -join '|') +
    '|stdlib:' + (Get-Sha256 $stdlibOriginal) +
    '|android:' + (Get-Sha256 $ANDROID_JAR)
$dexCurrent = (Test-Path (Join-Path $libDex 'classes.dex')) -and
    (Test-Path $dexMarker) -and
    ((Get-Content $dexMarker -Raw).Trim() -eq $dexSignature)
if (-not $dexCurrent) {
    Write-Host 'dexing the library cache (one time; a few minutes)'
    $libDexTemp = Join-Path $work 'libdex.tmp'
    if (Test-Path $libDexTemp) { Remove-Item $libDexTemp -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $libDexTemp | Out-Null
    $d8args = @('-Xmx4g', '-cp', $D8, 'com.android.tools.r8.D8',
        '--min-api', '24', '--lib', $ANDROID_JAR, '--output', $libDexTemp) +
        (Get-ChildItem (Join-Path $libStage 'jars') -Filter *.jar | ForEach-Object { $_.FullName }) +
        $stdlibOriginal
    & $JAVA @d8args
    if ($LASTEXITCODE -ne 0) { throw "D8 failed for the library cache (exit $LASTEXITCODE)" }
    if (-not (Test-Path (Join-Path $libDexTemp 'classes.dex'))) {
        throw 'D8 produced no classes.dex for the library cache'
    }
    if (Test-Path $libDex) { Remove-Item $libDex -Recurse -Force }
    Move-Item $libDexTemp $libDex
    Set-Content -Path $dexMarker -Value $dexSignature -Encoding UTF8
}
New-Item -ItemType Directory -Force -Path (Join-Path $libStage 'dex') | Out-Null
Copy-Item (Join-Path $libDex 'classes*.dex') (Join-Path $libStage 'dex') -Force
Write-Host ("library dex: " + ((Get-ChildItem (Join-Path $libStage 'dex')).Count) + ' files')

$index = [ordered]@{ version = 1; modules = $modules }
$indexJson = $index | ConvertTo-Json -Depth 5
Set-Content -Path (Join-Path $libStage 'libs.json') -Value $indexJson -Encoding UTF8

$libsZip = Join-Path $out 'libraries.zip'
if (Test-Path $libsZip) { Remove-Item $libsZip -Force }
# CreateFromDirectory on Windows writes "\" separators into the entry names. A zip is
# supposed to use "/", and the phone (Linux) treats a backslash as part of the file name,
# so the whole cache would unpack as a flat pile and the index would find no dex. Write
# the entries ourselves with forward slashes.
$zip = [System.IO.Compression.ZipFile]::Open(
    $libsZip, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem -Path $libStage -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($libStage.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $_.FullName, $rel,
            [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
}
Write-Host ("packed libraries.zip (" + $modules.Count + ' modules, ' +
    [Math]::Round((Get-Item $libsZip).Length / 1MB, 1) + ' MB)')

Write-Host ''
Write-Host '5/5 toolchain.json'

$entries = New-Object System.Collections.Generic.List[object]
foreach ($file in $files) {
    if ($file.dex) {
        $name = $produced[$file.id]
        $path = Join-Path $out $name
    } else {
        # stdlib ships as-is: it is a library to compile against, not a program to run
        $name = "$($file.id)-$($file.version).jar"
        $path = Join-Path $out $name
        Copy-Item $originals[$file.id] $path -Force
    }
    $item = Get-Item $path
    $entries.Add([ordered]@{
        id = $file.id
        version = $file.version
        fileName = $name
        url = "$BaseUrl$name"
        sizeBytes = $item.Length
        sha256 = (Get-Sha256 $path)
        license = $file.license
    })
}
$androidItem = Get-Item $androidOut
$entries.Add([ordered]@{
    id = 'android-jar'
    version = '35'
    fileName = 'android-35.jar'
    url = "$($BaseUrl)android-35.jar"
    sizeBytes = $androidItem.Length
    sha256 = (Get-Sha256 $androidOut)
    license = 'Android SDK License'
})
$libsItem = Get-Item $libsZip
$entries.Add([ordered]@{
    id = 'libraries'
    version = '1'
    fileName = 'libraries.zip'
    url = "$($BaseUrl)libraries.zip"
    sizeBytes = $libsItem.Length
    sha256 = (Get-Sha256 $libsZip)
    license = 'Apache-2.0 (AndroidX, Kotlin, Guava)'
})

$manifest = [ordered]@{
    version = '1'
    kotlin = '1.9.24'
    composeCompiler = '1.5.14'
    compileSdk = 35
    minSdkFloor = 24
    aapt2 = [ordered]@{
        version = '34.0.4'
        license = 'Apache-2.0 (AOSP aapt2); arm64 build from AndroidIDEOfficial/androidide-tools (GPL-3.0 repository)'
        source = 'https://github.com/AndroidIDEOfficial/androidide-tools/releases/tag/v34.0.4'
    }
    files = $entries
}
$json = $manifest | ConvertTo-Json -Depth 6
# ConvertTo-Json writes \u escapes for some characters; keep it readable and stable.
$json = [regex]::Replace($json, '\\u0026', '&')
Set-Content -Path (Join-Path $assets 'toolchain.json') -Value $json -Encoding UTF8

Write-Host ''
Write-Host "pack ready in $out"
$total = (Get-ChildItem $out | Measure-Object -Property Length -Sum).Sum
Write-Host ("total size: " + [Math]::Round($total / 1MB, 1) + ' MB')
Get-ChildItem $out | ForEach-Object { Write-Host ("  " + $_.Name + '  ' + [Math]::Round($_.Length / 1MB, 1) + ' MB') }
Write-Host ''
Write-Host "Upload every file in $out to:"
Write-Host "  $BaseUrl"
Write-Host 'then the app can download the pack.'
