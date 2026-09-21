./gradlew build

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build successful! Copying to server and clients..." -ForegroundColor Green
    
    $destinations = @(
        "C:\Users\Frost\Desktop\Server 1.21.1\mods\",
        "C:\Users\Frost\AppData\Roaming\PrismLauncher\instances\1.21.1 Minigames\minecraft\mods\",
        "C:\Users\Frost\AppData\Roaming\PrismLauncher\instances\1.21.1 Minigames 2\minecraft\mods\"
    )

    $lockedFound = $false
    foreach ($dest in $destinations) {
        $targetJar = Join-Path $dest "miniverse-1.0.0.jar"
        if (Test-Path $targetJar) {
            try {
                $stream = [System.IO.File]::Open($targetJar, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
                if ($stream) { $stream.Close() }
            } catch {
                Write-Host "[WARNING] $targetJar is currently locked by a running Java process (Server or Client)!" -ForegroundColor Yellow
                Write-Host "Hot-swapping a jar while the JVM is running causes ZipException crashes when new classes are loaded." -ForegroundColor Yellow
                Write-Host "Please stop and restart the server/client after copying." -ForegroundColor Yellow
                $lockedFound = $true
            }
        }
        Remove-Item "$dest\miniverse-*-sources.jar" -ErrorAction SilentlyContinue
        Copy-Item "build\libs\miniverse-1.0.0.jar" -Destination $dest -Force
    }
    
    if ($lockedFound) {
        Write-Host "Deployment finished with warnings: RESTART your server/client to apply changes cleanly!" -ForegroundColor Yellow
    } else {
        Write-Host "Deployment complete!" -ForegroundColor Cyan
    }
} else {
    Write-Host "Build failed! Fix the errors before deploying." -ForegroundColor Red
}
