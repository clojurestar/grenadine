param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]] $GrenadineArgs
)

$ErrorActionPreference = 'Stop'
$ReleaseUrl = if ($env:GRENADINE_RELEASE_URL) {
  $env:GRENADINE_RELEASE_URL
} else {
  'https://github.com/clojurestar/grenadine/releases/latest/download'
}

$Architecture = switch ($env:PROCESSOR_ARCHITECTURE) {
  'AMD64' { 'amd64' }
  'ARM64' { 'arm64' }
  default { throw "grenadine get: unsupported Windows architecture: $env:PROCESSOR_ARCHITECTURE" }
}
$Platform = "windows_$Architecture"
$Checksums = (Invoke-RestMethod "$ReleaseUrl/grenadine-checksums.txt") -split "`n"
$Line = $Checksums | Where-Object { $_ -match "grenadine-[^ ]*-$Platform\.zip\s*$" } | Select-Object -First 1
if (-not $Line) { throw "grenadine get: release has no archive for $Platform" }
$Fields = $Line.Trim() -split '\s+'
$Checksum = $Fields[0].ToLowerInvariant()
$Archive = $Fields[-1].TrimStart('*')
$ReleaseName = $Archive.Substring(0, $Archive.Length - 4)
$CacheRoot = Join-Path ([IO.Path]::GetTempPath()) "grenadine-get\$ReleaseName"
$ArchivePath = Join-Path $CacheRoot $Archive
$Marker = Join-Path $CacheRoot '.sha256'

$CachedChecksum = if (Test-Path $Marker) { (Get-Content $Marker -Raw).Trim() } else { '' }
if ($CachedChecksum -ne $Checksum) {
  Remove-Item $CacheRoot -Recurse -Force -ErrorAction SilentlyContinue
  New-Item $CacheRoot -ItemType Directory -Force | Out-Null
  Invoke-WebRequest "$ReleaseUrl/$Archive" -OutFile $ArchivePath
  $Actual = (Get-FileHash $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($Actual -ne $Checksum) { throw "grenadine get: checksum mismatch for $Archive" }
  Expand-Archive $ArchivePath -DestinationPath $CacheRoot -Force
  Set-Content $Marker $Checksum -NoNewline
}

$Executable = Get-ChildItem $CacheRoot -Filter grenadine.exe -File -Recurse | Select-Object -First 1
if (-not $Executable) { throw 'grenadine get: archive did not contain grenadine.exe' }
& $Executable.FullName --version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'grenadine get: downloaded executable failed its version check' }
& $Executable.FullName @GrenadineArgs
exit $LASTEXITCODE
