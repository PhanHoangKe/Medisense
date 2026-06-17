$oldPkg = "com.example.medisense"
$newPkg = "vn.medisense.app"

$oldPathMain = "d:\MediSense\app\src\main\java\com\example\medisense"
$newPathMain = "d:\MediSense\app\src\main\java\vn\medisense\app"

$oldPathTest = "d:\MediSense\app\src\test\java\com\example\medisense"
$newPathTest = "d:\MediSense\app\src\test\java\vn\medisense\app"

$oldPathAndroidTest = "d:\MediSense\app\src\androidTest\java\com\example\medisense"
$newPathAndroidTest = "d:\MediSense\app\src\androidTest\java\vn\medisense\app"

# Construct moving logic
function Move-PackageDir($old, $new) {
    if (Test-Path $old) {
        New-Item -ItemType Directory -Force -Path $new | Out-Null
        Copy-Item -Path "$old\*" -Destination $new -Recurse -Force
    }
}

Move-PackageDir $oldPathMain $newPathMain
Move-PackageDir $oldPathTest $newPathTest
Move-PackageDir $oldPathAndroidTest $newPathAndroidTest

# Cleanup old com directory carefully
if (Test-Path "d:\MediSense\app\src\main\java\com\") { Remove-Item -Path "d:\MediSense\app\src\main\java\com\" -Recurse -Force }
if (Test-Path "d:\MediSense\app\src\test\java\com\") { Remove-Item -Path "d:\MediSense\app\src\test\java\com\" -Recurse -Force }
if (Test-Path "d:\MediSense\app\src\androidTest\java\com\") { Remove-Item -Path "d:\MediSense\app\src\androidTest\java\com\" -Recurse -Force }

# Replacer
$files = Get-ChildItem -Path "d:\MediSense\app\" -Include *.java, *.xml, *.kts, *.pro -Recurse
foreach ($file in $files) {
    $path = $file.FullName
    $text = [IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    if ($text.Contains($oldPkg)) {
        $newText = $text.Replace($oldPkg, $newPkg)
        [IO.File]::WriteAllText($path, $newText, [System.Text.Encoding]::UTF8)
        Write-Host "Updated: $path"
    }
}

# Fix proguard if necessary
Write-Host "Refactor completed successfully."
