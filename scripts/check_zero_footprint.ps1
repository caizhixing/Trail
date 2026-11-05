#Requires -Version 5.1
<#
.SYNOPSIS
    Zero-footprint acceptance check for the Trail library (issue #2, workflow
    PRD docs/prd/trail-lib-v1.md section 4, behavior 2.11).

.DESCRIPTION
    Checks, all against the offline-frozen toolchain (every gradle call runs
    --offline):

      1. productionReleaseRuntimeClasspath of :sample-app contains NO trail-debug.
      2. Every classes*.dex inside the productionRelease APK contains NO
         io/github/caizhixing/trail library string and NO "trail_sample_"
         event-name marker string.
      3. Reverse assertion: internalReleaseRuntimeClasspath DOES contain
         trail-debug.

    Dex scan pattern note: the sample app namespace itself is
    io.github.caizhixing.trail.sample (frozen in sample-app/build.gradle.kts)
    and R8 keeps the manifest launcher class name, so the sample's own
    descriptor "io/github/caizhixing/trail/sample/..." is always present.
    The library-footprint pattern therefore excludes exactly that prefix:
        io/github/caizhixing/trail(?!/sample)
    Kotlin @Metadata stores package names in dot form (io.github.caizhixing...)
    which never matches the slash form used here.

    withTrace shell evidence: sample-app keeps a live (unguardable) call to
    Trail.withTrace in production code (SampleTraceSeed), so check 2 passing
    proves R8 fully removed the suspend shell class; report the outcome on
    the issue (D-1 backfill).

.NOTES
    Exit codes: 0 = all green, 1 = at least one check failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $repoRoot 'gradlew.bat'
$apkRoot = Join-Path $repoRoot (Join-Path 'sample-app' (Join-Path 'build' (Join-Path 'outputs' 'apk')))

$script:failures = @()

function Write-Pass([string]$Message) { Write-Host ("[PASS] " + $Message) -ForegroundColor Green }

function Write-Fail([string]$Message) {
    Write-Host ("[FAIL] " + $Message) -ForegroundColor Red
    $script:failures += $Message
}

function Get-DependencyTree([string]$Configuration) {
    $output = & $gradlew --offline --quiet ':sample-app:dependencies' --configuration $Configuration 2>$null | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "gradle dependencies failed for configuration '$Configuration' (exit code $LASTEXITCODE)"
    }
    return $output
}

function Get-ReleaseApk([string]$Flavor) {
    $dir = Join-Path $apkRoot (Join-Path $Flavor 'release')
    if (-not (Test-Path -LiteralPath $dir)) { return $null }
    $apk = Get-ChildItem -LiteralPath $dir -Filter '*.apk' | Select-Object -First 1
    if ($null -eq $apk) { return $null }
    return $apk.FullName
}

$libraryPattern = 'io/github/caizhixing/trail(?!/sample)'
$markerPattern = 'trail_sample_'
$latin1 = [System.Text.Encoding]::GetEncoding('ISO-8859-1')

# ---------------------------------------------------------------------------
# Check 1 + 3: runtime classpath trees
# ---------------------------------------------------------------------------
Write-Host '== dependency tree checks ==' -ForegroundColor Cyan
$productionTree = Get-DependencyTree 'productionReleaseRuntimeClasspath'
$internalTree = Get-DependencyTree 'internalReleaseRuntimeClasspath'

if ($productionTree -match 'trail-debug') {
    Write-Fail 'productionReleaseRuntimeClasspath contains trail-debug (zero-footprint violated)'
} else {
    Write-Pass 'productionReleaseRuntimeClasspath has no trail-debug'
}

if ($internalTree -match 'trail-debug') {
    Write-Pass 'internalReleaseRuntimeClasspath contains trail-debug (reverse assertion)'
} else {
    Write-Fail 'internalReleaseRuntimeClasspath does NOT contain trail-debug (internal wiring broken)'
}

if ($productionTree -match 'trail-noop') {
    Write-Host '[INFO] productionReleaseRuntimeClasspath carries trail-noop (expected)'
} else {
    Write-Host '[WARN] productionReleaseRuntimeClasspath does not mention trail-noop (unexpected, not a contract check)'
}

# ---------------------------------------------------------------------------
# Ensure both release APKs exist (assemble offline when missing)
# ---------------------------------------------------------------------------
$productionApk = Get-ReleaseApk 'production'
$internalApk = Get-ReleaseApk 'internal'
if ($null -eq $productionApk -or $null -eq $internalApk) {
    Write-Host '[INFO] release APK missing, assembling offline (internal + production)...'
    & $gradlew --offline ':sample-app:assembleInternalRelease' ':sample-app:assembleProductionRelease' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Fail 'gradle offline assemble of release variants failed'
        Write-Host ('zero-footprint check FAILED (' + $script:failures.Count + ' failures)') -ForegroundColor Red
        exit 1
    }
    $productionApk = Get-ReleaseApk 'production'
    $internalApk = Get-ReleaseApk 'internal'
}

if ($null -eq $productionApk) {
    Write-Fail 'productionRelease APK not found after assemble'
}
if ($null -eq $internalApk) {
    Write-Fail 'internalRelease APK not found after assemble'
}

# ---------------------------------------------------------------------------
# Check 2: byte-scan every classes*.dex of the productionRelease APK
# ---------------------------------------------------------------------------
if ($null -ne $productionApk) {
    Write-Host '== dex byte-scan checks ==' -ForegroundColor Cyan
    Write-Host ("[INFO] scanning " + $productionApk)

    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $zip = [System.IO.Compression.ZipFile]::OpenRead($productionApk)
    try {
        $dexEntries = @($zip.Entries | Where-Object { $_.Name -match '^classes\d*\.dex$' })
        if ($dexEntries.Count -eq 0) {
            Write-Fail 'no classes*.dex entries found in the productionRelease APK'
        }
        foreach ($entry in $dexEntries) {
            $bytes = $null
            $stream = $entry.Open()
            try {
                $buffer = New-Object System.IO.MemoryStream
                $stream.CopyTo($buffer)
                $bytes = $buffer.ToArray()
            } finally {
                $stream.Dispose()
            }
            $text = $latin1.GetString($bytes)

            $libraryHits = [regex]::Matches($text, $libraryPattern)
            if ($libraryHits.Count -gt 0) {
                Write-Fail ($entry.FullName + ': io/github/caizhixing/trail library string present x' + $libraryHits.Count)
            } else {
                Write-Pass ($entry.FullName + ': no io/github/caizhixing/trail library string')
            }

            $markerHits = [regex]::Matches($text, $markerPattern)
            if ($markerHits.Count -gt 0) {
                Write-Fail ($entry.FullName + ': trail_sample_ marker string present x' + $markerHits.Count)
            } else {
                Write-Pass ($entry.FullName + ': no trail_sample_ marker string')
            }
        }
    } finally {
        $zip.Dispose()
    }

    Write-Host '[INFO] withTrace shell evidence: sample-app keeps a live Trail.withTrace call site (SampleTraceSeed), so the library-string scan above proves the R8 fate of the suspend shell class'
}

# ---------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------
if ($script:failures.Count -gt 0) {
    Write-Host ('zero-footprint check FAILED (' + $script:failures.Count + ' failure(s))') -ForegroundColor Red
    exit 1
}

Write-Host 'zero-footprint check PASSED (all checks green)' -ForegroundColor Green
exit 0
