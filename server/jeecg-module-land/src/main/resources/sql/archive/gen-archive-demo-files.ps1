<#
.SYNOPSIS
    为「档案管理」演示数据补齐磁盘文件。

.DESCRIPTION
    读取 tj-jyxyd 库 t_archive_file 里 id like 'demo-af-%' 的记录，
    按 store_path 在 jeecg.path.upload 目录下生成真实文件。

    为什么需要这一步：
      · 数据库里只存相对路径（store_path），文件本体在上传目录；
      · 没有磁盘文件时，档案「下载」会提示文件不存在，
        「按项目导出 ZIP」会把缺失文件写成「.缺失说明.txt」并在清单里标注「文件缺失」；
      · 补齐之后，下载、打包导出、档案清单.xlsx 三者都能真实跑通。

    文件内容策略（按扩展名）：
      · .pdf          → 生成结构完整、可正常打开的 1 页 PDF（含正确 xref 表）
      · .jpg / .png   → 用 System.Drawing 生成一张带文字的图片（失败则退化为文本）
      · .zip          → 用 Compress-Archive 生成真实 zip（内含说明文本）
      · 其它（dwg/docx/xlsx 等）→ 生成说明性文本占位文件

    ★ 大小对齐：生成后按数据库登记的 file_size 做「填充」，保证
      「列表显示的大小 == 实际文件大小 == 导出的字节数」三者一致。
      · PDF：填充写成一个注释块，放在最后一个对象之后、xref 之前
        —— 必须保证 startxref / %%EOF 落在文件末尾 1KB 内，否则阅读器找不到交叉引用表；
      · 图片 / zip / 文本：填充追加在结束标记之后，解码器与压缩工具会忽略。

.PARAMETER UploadRoot
    上传根目录，默认取 application-dev.yml 的 jeecg.path.upload。

.PARAMETER Clean
    只清理：删除 {UploadRoot}/archive 下演示数据对应的文件，不生成。

.EXAMPLE
    pwsh -File gen-archive-demo-files.ps1
    pwsh -File gen-archive-demo-files.ps1 -Clean

.NOTES
    依赖：本机 MySQL 5.7 的 tj-jyxyd 库、mysql 命令行客户端（或用 -CsvFromFile 传入清单）。
#>
[CmdletBinding()]
param(
    [string]$UploadRoot = 'E:/work-space/upFiles/tj-land-use-info',
    [string]$MySqlExe   = 'mysql',
    [string]$DbHost     = '127.0.0.1',
    [int]   $DbPort     = 3306,
    [string]$DbUser     = 'root',
    [string]$DbPass     = 'root',
    [string]$DbName     = 'tj-jyxyd',
    # 也可以先把清单导出成 CSV（store_path,file_name,file_ext,file_size），再用它生成
    [string]$CsvFromFile,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 1) 取演示文件的清单
# ---------------------------------------------------------------------------
$sql = @"
SELECT CONCAT_WS(',', store_path, REPLACE(file_name, ',', ' '), file_ext, file_size)
FROM t_archive_file
WHERE del_flag = 0 AND id LIKE 'demo-af-%'
ORDER BY store_path;
"@

if ($CsvFromFile) {
    $rows = Get-Content -LiteralPath $CsvFromFile -Encoding UTF8 | Where-Object { $_.Trim() -ne '' }
} else {
    $mysqlArgs = @("--host=$DbHost", "--port=$DbPort", "--user=$DbUser", "--database=$DbName",
                   '--batch', '--raw', '--skip-column-names', "--execute=$sql")
    if ($DbPass) { $mysqlArgs += "--password=$DbPass" }
    $rows = & $MySqlExe @mysqlArgs
    if ($LASTEXITCODE -ne 0) { throw "查询演示文件清单失败（mysql 退出码 $LASTEXITCODE）" }
    $rows = $rows | Where-Object { $_ -and $_.Trim() -ne '' }
}

if (-not $rows) { Write-Host '没有查到演示文件记录，请先执行 07_t_archive_demo_data.sql' -ForegroundColor Yellow; return }

Write-Host ("演示文件共 {0} 个，上传根目录：{1}" -f $rows.Count, $UploadRoot) -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# 2) 生成器
# ---------------------------------------------------------------------------

# 生成一份结构完整、可正常打开的 1 页 PDF（ASCII 文本，含正确 xref 偏移）
function New-PdfBytes {
    param([string]$Text, [int]$TargetSize)

    # PDF 正文用 ASCII，中文替换成 '?'，避免依赖字体嵌入
    $ascii = -join ($Text.ToCharArray() | ForEach-Object {
        if ([int]$_ -lt 128) { $_ } else { '?' }
    })
    $ascii = $ascii -replace '[\\(\)]', ''
    $content = "BT /F1 16 Tf 60 770 Td ($ascii) Tj ET"

    $objs = @(
        '<</Type/Catalog/Pages 2 0 R>>',
        '<</Type/Pages/Kids[3 0 R]/Count 1>>',
        '<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>',
        '<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>',
        ("<</Length {0}>>`nstream`n{1}`nendstream" -f $content.Length, $content)
    )

    # 把整份 PDF 的拼装写成一个脚本块，便于「先量长度、再按需插入注释块」两遍构建
    $build = {
        param([string]$Pad)
        $sb = New-Object Text.StringBuilder
        [void]$sb.Append("%PDF-1.4`n")
        $offsets = @()
        for ($i = 0; $i -lt $objs.Count; $i++) {
            $offsets += $sb.Length
            [void]$sb.Append(("{0} 0 obj`n{1}`nendobj`n" -f ($i + 1), $objs[$i]))
        }
        # ★ 填充放在最后一个对象之后、xref 之前：PDF 允许对象之间出现注释，
        #   且必须保证 startxref / %%EOF 落在文件末尾 1KB 内（否则阅读器找不到交叉引用表）。
        if ($Pad) { [void]$sb.Append($Pad) }
        $xrefPos = $sb.Length
        [void]$sb.Append(("xref`n0 {0}`n" -f ($objs.Count + 1)))
        [void]$sb.Append("0000000000 65535 f `n")
        foreach ($off in $offsets) {
            [void]$sb.Append(("{0:D10} 00000 n `n" -f $off))
        }
        [void]$sb.Append(("trailer`n<</Size {0}/Root 1 0 R>>`nstartxref`n{1}`n%%EOF`n" -f ($objs.Count + 1), $xrefPos))
        $sb.ToString()
    }

    $text = & $build ''
    # ★ 迭代逼近到精确字节数：
    #   加入填充后 startxref 的数值位数会变（如 400 → 1887436），文件总长会跟着多出几位，
    #   所以第一次一定偏大；这里按「还差多少字节」反复微调填充长度，直到差额为 0。
    $padLen = 0
    for ($iter = 0; $iter -lt 8; $iter++) {
        $cur = [Text.Encoding]::ASCII.GetByteCount($text)
        $delta = $TargetSize - $cur
        if ($delta -eq 0) { break }
        $padLen = $padLen + $delta
        # 注释块首尾各占 1 字节（'%' 与换行），因此至少需要 2 字节才拼得出来
        if ($padLen -lt 2) { $padLen = 2 }
        $pad = '%' + (' ' * ($padLen - 2)) + "`n"
        $text = & $build $pad
    }
    return [Text.Encoding]::ASCII.GetBytes($text)
}

function New-ImageBytes {
    param([string]$Text, [int]$TargetSize, [string]$Ext)
    try {
        Add-Type -AssemblyName System.Drawing
        $bmp = New-Object System.Drawing.Bitmap 1240, 1754     # A4 @150dpi
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.Clear([System.Drawing.Color]::White)
        $g.DrawRectangle((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(210, 215, 225)), 3), 40, 40, 1160, 1674)
        $font = New-Object System.Drawing.Font 'Arial', 34
        $g.DrawString('TJ Land Archive - Demo File', $font, [System.Drawing.Brushes]::Black, 90, 130)
        $g.DrawString('(placeholder image for demo data)', (New-Object System.Drawing.Font 'Arial', 22), [System.Drawing.Brushes]::Gray, 90, 200)
        $g.Dispose()
        $ms = New-Object System.IO.MemoryStream
        if ($Ext -eq 'png') { $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png) }
        else                { $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Jpeg) }
        $bmp.Dispose()
        $bytes = $ms.ToArray(); $ms.Dispose()
        if ($TargetSize -gt $bytes.Length) {
            $pad = New-Object byte[] ($TargetSize - $bytes.Length)
            $bytes = $bytes + $pad   # 图片结束标记之后填充，解码器会忽略
        }
        return $bytes
    } catch {
        Write-Warning ("生成图片失败（{0}），退化为文本占位：{1}" -f $Text, $_.Exception.Message)
        return (New-TextBytes -Text $Text -TargetSize $TargetSize)
    }
}

function New-TextBytes {
    param([string]$Text, [int]$TargetSize)
    $head = @"
================================================================
 天津市经营性用地市政基础设施配套动态监管工作站
 档案管理 —— 演示占位文件（非真实业务文件）
 文件：$Text
 说明：此文件由 gen-archive-demo-files.ps1 生成，仅用于打通
       「文件下载」与「按项目导出 ZIP」两条链路。
================================================================

"@
    $bytes = [Text.Encoding]::UTF8.GetBytes($head)
    if ($TargetSize -gt $bytes.Length) {
        $pad = New-Object byte[] ($TargetSize - $bytes.Length)
        for ($i = 0; $i -lt $pad.Length; $i++) { $pad[$i] = 32 }
        $bytes = $bytes + $pad
    }
    return $bytes
}

function New-ZipBytes {
    param([string]$Text, [int]$TargetSize)
    $tmp = Join-Path ([IO.Path]::GetTempPath()) ("demo-zip-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tmp | Out-Null
    try {
        Set-Content -LiteralPath (Join-Path $tmp '登记材料清单.txt') -Encoding UTF8 -Value @"
天津市经营性用地市政基础设施配套动态监管工作站
档案管理演示数据 —— $Text
本压缩包用于验证「按项目导出 ZIP」链路。
"@
        $zipPath = Join-Path $tmp 'out.zip'
        Compress-Archive -Path (Join-Path $tmp '登记材料清单.txt') -DestinationPath $zipPath -Force
        $bytes = [IO.File]::ReadAllBytes($zipPath)
        if ($TargetSize -gt $bytes.Length) { $bytes = $bytes + (New-Object byte[] ($TargetSize - $bytes.Length)) }
        return $bytes
    } finally {
        Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
    }
}

# ---------------------------------------------------------------------------
# 3) 主流程
# ---------------------------------------------------------------------------
$root = (Resolve-Path -LiteralPath $UploadRoot -ErrorAction SilentlyContinue)
if (-not $root) {
    # 根目录不存在时按需创建（jeecg 上传时也会自行创建子目录）
    New-Item -ItemType Directory -Force -Path $UploadRoot | Out-Null
    $root = (Resolve-Path -LiteralPath $UploadRoot)
}
$rootPath = $root.Path

$created = 0; $removed = 0; $totalBytes = 0

foreach ($row in $rows) {
    $parts = $row -split ','
    if ($parts.Count -lt 4) { continue }
    $storePath = $parts[0].Trim()
    $fileName  = $parts[1].Trim()
    $ext       = $parts[2].Trim().ToLower()
    $size      = [int64]$parts[3]

    $rel  = $storePath.TrimStart('/', '\') -replace '/', [IO.Path]::DirectorySeparatorChar
    $full = Join-Path $rootPath $rel

    if ($Clean) {
        if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Force; $removed++ }
        continue
    }

    $dir = Split-Path -Parent $full
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

    $bytes = switch ($ext) {
        'pdf'         { New-PdfBytes   -Text $fileName -TargetSize $size }
        'jpg'         { New-ImageBytes -Text $fileName -TargetSize $size -Ext 'jpg' }
        'jpeg'        { New-ImageBytes -Text $fileName -TargetSize $size -Ext 'jpg' }
        'png'         { New-ImageBytes -Text $fileName -TargetSize $size -Ext 'png' }
        'zip'         { New-ZipBytes   -Text $fileName -TargetSize $size }
        default       { New-TextBytes  -Text $fileName -TargetSize $size }
    }
    [IO.File]::WriteAllBytes($full, $bytes)
    $created++
    $totalBytes += $bytes.Length
}

if ($Clean) {
    Write-Host ("已删除演示文件 {0} 个" -f $removed) -ForegroundColor Green
    # 顺手清掉空目录
    $archiveDir = Join-Path $rootPath 'archive'
    if (Test-Path -LiteralPath $archiveDir) {
        Get-ChildItem -LiteralPath $archiveDir -Recurse -Directory |
            Sort-Object { $_.FullName.Length } -Descending |
            Where-Object { -not (Get-ChildItem -LiteralPath $_.FullName -Force) } |
            Remove-Item -Force -ErrorAction SilentlyContinue
    }
} else {
    Write-Host ("已生成演示文件 {0} 个，合计 {1:N1} MB，根目录：{2}" -f `
        $created, ($totalBytes / 1MB), $rootPath) -ForegroundColor Green
    Write-Host '提示：文件按数据库登记的大小做了填充，可用 -Clean 一键清理。' -ForegroundColor DarkGray
}
