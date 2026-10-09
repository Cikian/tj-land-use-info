# 出图脚本：headless Edge 的 --window-size 偶发被忽略（输出被缩成 ~754x487），
# 这里对每张图校验尺寸，不符合就换参数重试，避免交付一版看起来对、其实被缩放过的图。
# 用法：pwsh -File perf/detail-redesign/screenshot.ps1
param()

$ErrorActionPreference = 'Continue'
Add-Type -AssemblyName System.Drawing

$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$base = 'file:///' + ($dir -replace '\\', '/')

$jobs = @(
  @{ Name = 'final-escalation';    W = 1912; H = 900 },
  @{ Name = 'final-archive';       W = 1912; H = 900 },
  @{ Name = 'regression-variants'; W = 1500; H = 1250 },
  @{ Name = 'compare';             W = 1500; H = 2400 }
)

function Get-Size ($path) {
  $im = [System.Drawing.Image]::FromFile($path)
  $s = @{ W = $im.Width; H = $im.Height }
  $im.Dispose()
  return $s
}

foreach ($job in $jobs) {
  $out = Join-Path $dir "$($job.Name).png"
  $ok = $false

  # ★ 只用 scale=1：scale=2 会把 CSS 视口宽度砍半，触发 --sd-cols 的响应式降列，
  #   量出来就不是目标宽度下的版面了。宁可多试几次，也不要一张「被缩放」的图。
  foreach ($attempt in 1..8) {
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    $mode = if ($attempt % 2 -eq 1) { '--headless=new' } else { '--headless' }
    $ud = Join-Path $env:TEMP ("edge-shot-" + [guid]::NewGuid().ToString('N').Substring(0, 8))

    & $edge $mode --disable-gpu --no-first-run --no-default-browser-check --hide-scrollbars `
      --force-device-scale-factor=1 --window-size="$($job.W),$($job.H)" --user-data-dir="$ud" `
      --screenshot="$out" "$base/$($job.Name).html" 2>&1 | Out-Null
    try { if (Test-Path $ud) { Remove-Item $ud -Recurse -Force -ErrorAction Stop } } catch { }

    if (-not (Test-Path $out)) { continue }
    $size = Get-Size $out
    # 允许 ±2px 误差
    if ([math]::Abs($size.W - $job.W) -le 2 -and [math]::Abs($size.H - $job.H) -le 2) {
      Write-Host ("OK   {0,-22} {1}x{2}  (attempt {3}, {4})" -f $job.Name, $size.W, $size.H, $attempt, $mode)
      $ok = $true
      break
    }
    Write-Host ("retry {0,-22} got {1}x{2} (attempt {3}, {4})" -f $job.Name, $size.W, $size.H, $attempt, $mode)
  }

  if (-not $ok) { Write-Warning "$($job.Name).png 尺寸未达标，请人工确认" }
}
