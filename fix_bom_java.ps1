param(
    [string[]]$Roots = @(
        ".\app\src\main\java",
        ".\app\src\test\java",
        ".\app\src\androidTest\java"
    )
)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$fixedFiles = @()

foreach ($root in $Roots) {
    if (-not (Test-Path -LiteralPath $root)) {
        continue
    }

    Get-ChildItem -LiteralPath $root -Recurse -File -Filter "*.java" | ForEach-Object {
        $path = $_.FullName
        $bytes = [System.IO.File]::ReadAllBytes($path)

        if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
            $content = [System.Text.Encoding]::UTF8.GetString($bytes, 3, $bytes.Length - 3)
            [System.IO.File]::WriteAllText($path, $content, $utf8NoBom)
            $fixedFiles += $path
        }
    }
}

Write-Host "Removed UTF-8 BOM from $($fixedFiles.Count) file(s)."
$fixedFiles | ForEach-Object { Write-Host " - $_" }

