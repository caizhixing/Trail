#Requires -Version 5.1
<#
.SYNOPSIS
    Zero-footprint acceptance check for the Trail library (issue #2 behavior
    2.11, upgraded by issue #8 behavior 8.5; workflow PRD
    docs/prd/trail-lib-v1.md section 4).

.DESCRIPTION
    Checks, all against the offline-frozen toolchain (every gradle call runs
    --offline):

      1. productionReleaseRuntimeClasspath of :sample-app contains NO trail-debug.
      2. Every classes*.dex inside the productionRelease APK contains NO
         io/github/caizhixing/trail library string and NO "trail_sample_"
         event-name marker string (the marker prefix covers the Kotlin AND the
         Java seeded call sites alike).
      3. Reverse assertion: internalReleaseRuntimeClasspath DOES contain
         trail-debug.
      4. Reverse byte-scan: the internalRelease APK dex contains at least one
         io/github/caizhixing/trail library string (excluding the /sample
         prefix, e.g. R8-kept Trail class descriptors) AND at least one
         "trail_sample_" marker string, proving check 2 absence comes from
         removal, not from a broken scan.
      5. Obfuscation-independent hard evidence from R8's own artifacts in
         sample-app/build/outputs/mapping/productionRelease/:
           5a. mapping.txt must NOT map any io.github.caizhixing.trail.<library
               class> (sample namespace excluded) to a live name — every
               library class entry must carry the R8$$REMOVED$$CLASS marker.
           5b. usage.txt must list those same library classes as removed.

     Why check 5 exists: with minification on, the dex namespace-string scan
     has a blind spot — R8 renames kept classes to arbitrary short names, so
     a passing string scan proves "no library string" but not "no library
     code". mapping.txt/usage.txt are R8's own removal records and are
     unaffected by obfuscation renaming; together with the marker-string scan
     they form the zero-footprint acceptance evidence (issue #8 behavior 8.5,
     adopted from the PR #10 review).

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
# Check 5: obfuscation-independent hard evidence (issue #8, behavior 8.5).
# R8 artifacts of the productionRelease build live under
# sample-app/build/outputs/mapping/productionRelease/.
#   5a. mapping.txt: every io.github.caizhixing.trail.<library class> entry
#       (sample namespace excluded) must map to R8$$REMOVED$$CLASS; a library
#       class kept under any live (original or renamed) name is a FAIL.
#   5b. usage.txt: those same library classes must be listed as removed.
# With minification on, the dex string scan above has a blind spot (R8 renames
# kept classes, so no library namespace string survives even if code does);
# mapping.txt/usage.txt are R8's own removal records and prove class-level
# removal regardless of obfuscation renaming.
# Class-scope note: the sample app's own io.github.caizhixing.trail.sample.*
# classes are NOT library classes (R8 legitimately keeps MainActivity for the
# manifest) and stay out of scope.
# ---------------------------------------------------------------------------
Write-Host '== mapping/usage hard-evidence checks ==' -ForegroundColor Cyan
$mappingDir = Join-Path $repoRoot (Join-Path 'sample-app' (Join-Path 'build' (Join-Path 'outputs' (Join-Path 'mapping' 'productionRelease'))))
$mappingPath = Join-Path $mappingDir 'mapping.txt'
$usagePath = Join-Path $mappingDir 'usage.txt'

if (-not (Test-Path -LiteralPath $mappingPath)) {
    Write-Fail ("productionRelease mapping.txt not found at " + $mappingPath + " (run :sample-app:assembleProductionRelease first)")
} elseif (-not (Test-Path -LiteralPath $usagePath)) {
    Write-Fail ("productionRelease usage.txt not found at " + $usagePath)
} else {
    # Class header lines only: "<original> -> <target>:". Member and inline
    # function lines are indented and therefore excluded by the anchored match.
    $classHeaderRegex = '^(io\.github\.caizhixing\.trail\.(?!sample\.)[^\s:]+) -> ([^\s:]+):'
    $removedMarker = 'R8$$REMOVED$$CLASS'

    $libraryClasses = @()
    $keptClasses = @()
    foreach ($line in (Get-Content -LiteralPath $mappingPath)) {
        if ($line.StartsWith(' ') -or $line.StartsWith("`t")) { continue }
        $headerMatch = [regex]::Match($line, $classHeaderRegex)
        if (-not $headerMatch.Success) { continue }
        $original = $headerMatch.Groups[1].Value
        $target = $headerMatch.Groups[2].Value
        $libraryClasses += $original
        if (-not $target.Contains($removedMarker)) {
            $keptClasses += ($original + ' -> ' + $target)
        }
    }

    if ($libraryClasses.Count -eq 0) {
        Write-Fail 'mapping.txt contains no io.github.caizhixing.trail.* library class entries (unexpected format or empty mapping)'
    } elseif ($keptClasses.Count -gt 0) {
        Write-Fail ('mapping.txt keeps trail library classes without the R8$$REMOVED$$CLASS marker: ' + ($keptClasses -join '; '))
    } else {
        Write-Pass ('mapping.txt: all ' + $libraryClasses.Count + ' trail library class entries map to R8$$REMOVED$$CLASS')
    }

    if ($libraryClasses.Count -gt 0) {
        $removedSet = New-Object 'System.Collections.Generic.HashSet[string]'
        foreach ($line in (Get-Content -LiteralPath $usagePath)) {
            $entry = $line.Trim()
            if ($entry.EndsWith(':')) { $entry = $entry.Substring(0, $entry.Length - 1) }
            if ($entry.Length -gt 0) { $removedSet.Add($entry) | Out-Null }
        }
        $missing = @($libraryClasses | Where-Object { -not $removedSet.Contains($_) })
        if ($missing.Count -gt 0) {
            Write-Fail ('usage.txt does not list these trail library classes as removed: ' + ($missing -join ', '))
        } else {
            Write-Pass ('usage.txt: all ' + $libraryClasses.Count + ' trail library classes listed as removed (obfuscation-independent hard evidence)')
        }
    }
}

# ---------------------------------------------------------------------------
# Check 4: reverse byte-scan of the internalRelease APK dex
# ---------------------------------------------------------------------------
if ($null -ne $internalApk) {
    Write-Host '== internal dex reverse-scan checks ==' -ForegroundColor Cyan
    Write-Host ("[INFO] scanning " + $internalApk)

    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $zip = [System.IO.Compression.ZipFile]::OpenRead($internalApk)
    try {
        $dexEntries = @($zip.Entries | Where-Object { $_.Name -match '^classes\d*\.dex$' })
        if ($dexEntries.Count -eq 0) {
            Write-Fail 'no classes*.dex entries found in the internalRelease APK'
        }
        $libraryTotal = 0
        $markerTotal = 0
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
            $libraryTotal += [regex]::Matches($text, $libraryPattern).Count
            $markerTotal += [regex]::Matches($text, $markerPattern).Count
        }
        if ($libraryTotal -ge 1) {
            Write-Pass ("internalRelease dex keeps the trail library string x" + $libraryTotal + " (reverse assertion)")
        } else {
            Write-Fail 'internalRelease dex contains NO io/github/caizhixing/trail library string (internal wiring broken)'
        }
        if ($markerTotal -ge 1) {
            Write-Pass ("internalRelease dex keeps the trail_sample_ marker string x" + $markerTotal + " (reverse assertion)")
        } else {
            Write-Fail 'internalRelease dex contains NO trail_sample_ marker string (sample events not wired into the internal build)'
        }
    } finally {
        $zip.Dispose()
    }
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
