$utf8NoBom = New-Object System.Text.UTF8Encoding $false

Get-ChildItem -Path "D:\MediSense\app\src\main\java" -Recurse -Include *.java,*.xml | ForEach-Object {
    if ($_.PsIsContainer) { return }
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $content = [System.IO.File]::ReadAllText($_.FullName)
        [System.IO.File]::WriteAllText($_.FullName, $content, $utf8NoBom)
        Write-Host "Fixed BOM for: $($_.FullName)"
    }
}
