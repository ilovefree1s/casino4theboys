param(
  [string]$OldTemplate,
  [string]$BakedOld,
  [string]$NewTemplate,
  [string]$Out,
  [string]$MapFile = "assets.json"
)
$ErrorActionPreference = "Stop"
$rx = [regex]'__[A-Z0-9_]+__'

# The asset map is recovered once from a known-good bake, then cached: the
# baker that made it originally lived in a scratchpad and did not survive.
if (Test-Path $MapFile) {
  $map = @{}
  (Get-Content $MapFile -Raw | ConvertFrom-Json).PSObject.Properties |
    ForEach-Object { $map[$_.Name] = $_.Value }
  "loaded $($map.Count) assets from $MapFile"
} else {
  $old = [IO.File]::ReadAllText($OldTemplate)
  $baked = [IO.File]::ReadAllText($BakedOld)
  $names = @(); $lits = @(); $pos = 0
  foreach ($m in $rx.Matches($old)) {
    $lits += $old.Substring($pos, $m.Index - $pos); $names += $m.Value
    $pos = $m.Index + $m.Length
  }
  $lits += $old.Substring($pos)
  $map = @{}
  if (-not $baked.StartsWith($lits[0])) { throw "baked file does not start with first literal run" }
  $bp = $lits[0].Length
  for ($i = 0; $i -lt $names.Count; $i++) {
    $next = $lits[$i + 1]
    if ($next.Length -eq 0) { throw ("empty literal run after " + $names[$i]) }
    $anchor = if ($next.Length -gt 60) { $next.Substring(0, 60) } else { $next }
    $at = $baked.IndexOf($anchor, $bp, [StringComparison]::Ordinal)
    if ($at -lt 0) { throw ("lost the trail at " + $names[$i]) }
    $val = $baked.Substring($bp, $at - $bp)
    if ($map.ContainsKey($names[$i])) {
      if ($map[$names[$i]] -ne $val) { throw ($names[$i] + " resolved two ways") }
    } else { $map[$names[$i]] = $val }
    $bp = $at + $next.Length
  }
  $map | ConvertTo-Json -Compress -Depth 3 | Set-Content $MapFile -Encoding utf8
  "recovered $($map.Count) assets and cached them in $MapFile"
}

$new = [IO.File]::ReadAllText($NewTemplate)
$missing = @()
foreach ($m in $rx.Matches($new)) {
  if (-not $map.ContainsKey($m.Value) -and $missing -notcontains $m.Value) { $missing += $m.Value }
}
if ($missing.Count) { throw ("no asset for: " + ($missing -join ", ")) }
foreach ($k in $map.Keys) { $new = $new.Replace($k, $map[$k]) }
if ($rx.Matches($new).Count) { throw "placeholders left after baking" }
[IO.File]::WriteAllText($Out, $new)
"baked " + $Out + " size " + (Get-Item $Out).Length
