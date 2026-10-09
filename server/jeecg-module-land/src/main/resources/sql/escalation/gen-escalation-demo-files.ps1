<#
.SYNOPSIS
    为「提级论证管理」演示数据补齐磁盘文件。

.DESCRIPTION
    读取 tj-jyxyd 库 t_escalation_material 里 id like 'demo-em-%' 的记录，
    按 store_path 在 jeecg.path.upload 目录下生成真实文件。

    为什么需要这一步：
      · 数据库里只存相对路径（store_path），文件本体在上传目录；
      · 没有磁盘文件时，材料「下载 / 在线预览」会提示文件不存在；
      · 补齐之后，下载与在线预览才能真实跑通。

    文件内容策略（按扩展名）：
      · .pdf   → 生成结构完整、可正常打开的 1 页 PDF（含正确 xref 表）
      · .docx  → 生成**真正的 docx**（标准 OOXML zip），Word 可直接打开
      · .xlsx  → 生成**真正的 xlsx**（标准 OOXML zip），Excel 可直接打开
      · .jpg   → 用 System.Drawing 生成带文字的图片（失败则退化为文本占位）
      · 其它   → 生成说明性文本占位文件

    ★ 大小对齐：生成后按数据库登记的 file_size 做「填充」，保证
      「列表显示的大小 == 实际文件大小」一致，界面上的 12.4MB / 8.7MB 与原型一致。
      · PDF：填充写成一个注释块（% 开头），放在最后一个对象之后、xref 之前
        —— 必须保证 startxref / %%EOF 落在文件末尾 1KB 内，否则阅读器找不到交叉引用表；
      · OOXML（docx/xlsx）：填充写成 zip 内一个**不压缩**（stored）的 padding.bin 条目
        —— 不能用「在 zip 后面追加字节」的办法：zip 的中央目录（EOCD）必须落在文件末尾
          64KB 内，追加几 MB 填充会让 Word/Excel 找不到 EOCD 而报文件损坏；
      · 图片 / 文本：填充追加在结束标记之后，解码器会忽略。

.PARAMETER UploadRoot
    上传根目录，默认取 application-dev.yml 的 jeecg.path.upload。

.PARAMETER Clean
    只清理：删除 {UploadRoot}/escalation 下演示数据对应的文件，不生成。

.EXAMPLE
    # 从库里取清单并生成文件
    pwsh -File gen-escalation-demo-files.ps1

    # 没有 mysql 客户端时，用同目录的清单文件
    pwsh -File gen-escalation-demo-files.ps1 -CsvFromFile .\demo-files-manifest.csv

    # 只清理
    pwsh -File gen-escalation-demo-files.ps1 -Clean

.NOTES
    依赖：本机 MySQL 5.7 的 tj-jyxyd 库、mysql 命令行客户端（或用 -CsvFromFile 传入清单）。
    清单 CSV 格式（无表头，与档案模块一致）：store_path,file_name,file_ext,file_size
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
    [string]$CsvFromFile,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

# ---------------------------------------------------------------------------
# 1) 取演示文件的清单
# ---------------------------------------------------------------------------
$sql = @"
SELECT CONCAT_WS(',', store_path, REPLACE(file_name, ',', ' '), file_ext, file_size)
FROM t_escalation_material
WHERE del_flag = 0 AND id LIKE 'demo-em-%'
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

if (-not $rows) { Write-Host '没有查到演示文件记录，请先执行 05_t_escalation_demo_data.sql（或用 -CsvFromFile）' -ForegroundColor Yellow; return }

Write-Host ("演示文件共 {0} 个，上传根目录：{1}" -f $rows.Count, $UploadRoot) -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# 2) 生成器
# ---------------------------------------------------------------------------

# 2.1 生成一份结构完整、可正常打开的 1 页 PDF（ASCII 文本，含正确 xref 偏移）
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

    $build = {
        param([string]$Pad)
        $sb = New-Object Text.StringBuilder
        [void]$sb.Append("%PDF-1.4`n")
        $offsets = @()
        for ($i = 0; $i -lt $objs.Count; $i++) {
            $offsets += $sb.Length
            [void]$sb.Append(("{0} 0 obj`n{1}`nendobj`n" -f ($i + 1), $objs[$i]))
        }
        if ($Pad) { [void]$sb.Append($Pad) }
        $xrefPos = $sb.Length
        [void]$sb.Append(("xref`n0 {0}`n" -f ($objs.Count + 1)))
        [void]$sb.Append("0000000000 65535 f `n")
        foreach ($off in $offsets) {
            [void]$sb.Append(("{0:D10} 00000 n `n" -f $off))
        }
        [void]$sb.Append(("trailer`n<</Size {0}/Root 1 0 R>>`nstartxref`n{1}`n%%EOF`n" -f ($objs.Count + 1), $xrefPos))
        [System.Text.Encoding]::ASCII.GetBytes($sb.ToString())
    }

    # 迭代逼近精确字节数：填充是「一个 % 注释行」，pad 字节数 = padLen + 1（换行），
    # 所以按差额直接调 padLen 即可；又因为 startxref 的数值位数可能随长度进位，
    # 需要多迭代几次收敛（最多 6 次，实测 2~3 次即精确命中）。
    $padLen = 1
    $bytes = $null
    for ($i = 0; $i -lt 6; $i++) {
        $pad = ('%' * $padLen) + "`n"
        $bytes = & $build $pad
        $diff = $TargetSize - $bytes.Length
        if ($diff -eq 0) { break }
        $padLen = $padLen + $diff
        if ($padLen -lt 1) { $padLen = 1 }
    }
    return $bytes
}

# 2.2 生成 OOXML（docx / xlsx）字节：真 zip + 不压缩的 padding 条目精确对齐大小
function New-OoxmlBytes {
    param(
        [ValidateSet('docx', 'xlsx')][string]$Kind,
        [string]$Text,
        [int]$TargetSize
    )

    $esc = { param($s) $s -replace '&', '&amp;' -replace '<', '&lt;' -replace '>', '&gt;' }

    if ($Kind -eq 'docx') {
        $entries = [ordered]@{
            '[Content_Types].xml' = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>
'@
            '_rels/.rels' = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>
'@
            'word/document.xml' = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
                '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>' +
                '<w:p><w:r><w:t xml:space="preserve">' + (& $esc $Text) + '</w:t></w:r></w:p>' +
                '<w:p><w:r><w:t xml:space="preserve">本文件为提级论证管理模块演示数据生成，内容为占位说明，不代表真实材料。</w:t></w:r></w:p>' +
                '</w:body></w:document>')
        }
    } else {
        $entries = [ordered]@{
            '[Content_Types].xml' = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>
'@
            '_rels/.rels' = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>
'@
            'xl/workbook.xml' = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="投资估算" sheetId="1" r:id="rId1"/></sheets></workbook>
'@
            'xl/_rels/workbook.xml.rels' = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>
'@
            'xl/worksheets/sheet1.xml' = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
                '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>' +
                '<row r="1"><c r="A1" t="inlineStr"><is><t>' + (& $esc $Text) + '</t></is></c></row>' +
                '<row r="2"><c r="A2" t="inlineStr"><is><t>本文件为提级论证管理模块演示数据生成，内容为占位说明。</t></is></c></row>' +
                '</sheetData></worksheet>')
        }
    }

    $enc = New-Object System.Text.UTF8Encoding($false)

    $build = {
        param([int]$Pad)
        $ms = New-Object System.IO.MemoryStream
        $zip = New-Object System.IO.Compression.ZipArchive($ms, [System.IO.Compression.ZipArchiveMode]::Create, $true)
        foreach ($name in $script:curEntries.Keys) {
            $entry = $zip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
            $stream = $entry.Open()
            $data = $script:curEnc.GetBytes($script:curEntries[$name])
            $stream.Write($data, 0, $data.Length)
            $stream.Dispose()
        }
        if ($Pad -gt 0) {
            # ★ 不压缩（stored）：保证「填充多少字节，文件就大多少字节」，便于精确对齐
            $padEntry = $zip.CreateEntry('padding.bin', [System.IO.Compression.CompressionLevel]::NoCompression)
            $ps = $padEntry.Open()
            $chunk = New-Object byte[] 65536
            $left = $Pad
            while ($left -gt 0) {
                $n = [Math]::Min($left, $chunk.Length)
                $ps.Write($chunk, 0, $n)
                $left -= $n
            }
            $ps.Dispose()
        }
        $zip.Dispose()
        $ms.ToArray()
    }

    $script:curEntries = $entries
    $script:curEnc = $enc

    $pad = 0
    $bytes = $null
    for ($i = 0; $i -lt 4; $i++) {
        $bytes = & $build $pad
        $diff = $TargetSize - $bytes.Length
        if ($diff -eq 0) { break }
        $pad += $diff
        if ($pad -lt 0) { $pad = 0 }
    }
    return $bytes
}

# 2.3 图片：优先真图，失败退化为文本占位
function New-ImageBytes {
    param([string]$Text, [int]$TargetSize)
    try {
        Add-Type -AssemblyName System.Drawing
        $w = 1200; $h = 800
        $bmp = New-Object System.Drawing.Bitmap($w, $h)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.Clear([System.Drawing.Color]::White)
        $font = New-Object System.Drawing.Font('Arial', 28)
        $g.DrawString($Text, $font, [System.Drawing.Brushes]::Black, 40, 60)
        $g.DrawString('提级论证管理 · 演示文件', (New-Object System.Drawing.Font('Microsoft YaHei', 20)), [System.Drawing.Brushes]::DimGray, 40, 140)
        $g.Dispose()
        $ms = New-Object System.IO.MemoryStream
        $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Jpeg)
        $bmp.Dispose()
        $bytes = $ms.ToArray()
        if ($bytes.Length -ge $TargetSize) { return $bytes }   # 图比目标大就不填充（保证能打开优先）
        $pad = New-Object byte[] ($TargetSize - $bytes.Length)
        return ($bytes + $pad)
    } catch {
        Write-Host ("    图片生成失败（{0}），退化为文本占位" -f $_.Exception.Message) -ForegroundColor DarkYellow
        return (New-TextBytes -Text $Text -TargetSize $TargetSize)
    }
}

# 2.4 文本占位
function New-TextBytes {
    param([string]$Text, [int]$TargetSize)
    $enc = New-Object System.Text.UTF8Encoding($false)
    $head = "演示占位文件`r`n$Text`r`n本文件由 gen-escalation-demo-files.ps1 生成，不代表真实材料。`r`n"
    $bytes = $enc.GetBytes($head)
    if ($bytes.Length -ge $TargetSize) { return $bytes }
    $pad = New-Object byte[] ($TargetSize - $bytes.Length)
    return ($bytes + $pad)
}

# ---------------------------------------------------------------------------
# 3) 清理模式
# ---------------------------------------------------------------------------
function Resolve-TargetPath {
    param([string]$Root, [string]$StorePath)
    $rel = $StorePath.TrimStart('/') -replace '/', [System.IO.Path]::DirectorySeparatorChar
    Join-Path $Root $rel
}

if ($Clean) {
    $n = 0
    foreach ($row in $rows) {
        $parts = $row -split ','
        if ($parts.Count -lt 4) { continue }
        $full = Resolve-TargetPath -Root $UploadRoot -StorePath $parts[0]
        if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Force; $n++ }
    }
    Write-Host ("已清理 {0} 个演示文件" -f $n) -ForegroundColor Green
    return
}

# ---------------------------------------------------------------------------
# 4) 生成
# ---------------------------------------------------------------------------
$okCount = 0
$failCount = 0

foreach ($row in $rows) {
    $parts = $row -split ','
    if ($parts.Count -lt 4) { Write-Host "跳过无法解析的行：$row" -ForegroundColor Yellow; continue }

    $storePath = $parts[0]
    $fileName  = $parts[1]
    $ext       = $parts[2].ToLower()
    [int]$size = $parts[3]

    $full = Resolve-TargetPath -Root $UploadRoot -StorePath $storePath
    $dir = Split-Path -Parent $full
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

    try {
        switch ($ext) {
            'pdf'  { $bytes = New-PdfBytes   -Text ("Escalation review material - " + [System.IO.Path]::GetFileNameWithoutExtension($storePath)) -TargetSize $size }
            'docx' { $bytes = New-OoxmlBytes -Kind docx -Text $fileName -TargetSize $size }
            'xlsx' { $bytes = New-OoxmlBytes -Kind xlsx -Text $fileName -TargetSize $size }
            'jpg'  { $bytes = New-ImageBytes -Text ([System.IO.Path]::GetFileNameWithoutExtension($storePath)) -TargetSize $size }
            'jpeg' { $bytes = New-ImageBytes -Text ([System.IO.Path]::GetFileNameWithoutExtension($storePath)) -TargetSize $size }
            'png'  { $bytes = New-ImageBytes -Text ([System.IO.Path]::GetFileNameWithoutExtension($storePath)) -TargetSize $size }
            default { $bytes = New-TextBytes -Text $fileName -TargetSize $size }
        }
        [System.IO.File]::WriteAllBytes($full, $bytes)
        $delta = $bytes.Length - $size
        $flag = if ($delta -eq 0) { '' } else { "（★ 与登记大小差 $delta 字节）" }
        Write-Host ("  [{0,-4}] {1,12:N0} B  {2}{3}" -f $ext, $bytes.Length, $storePath, $flag) -ForegroundColor Gray
        $okCount++
    } catch {
        Write-Host ("  生成失败：{0} —— {1}" -f $storePath, $_.Exception.Message) -ForegroundColor Red
        $failCount++
    }
}

Write-Host ("`n完成：成功 {0} 个，失败 {1} 个。目录：{2}" -f $okCount, $failCount, $UploadRoot) -ForegroundColor Green
if ($failCount -gt 0) { exit 1 }
