./gradlew build

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build successful! Copying to server and clients..." -ForegroundColor Green
    
    $destinations = @(
        "C:\Users\Frost\Desktop\Server 1.21.1\mods\",
        "C:\Users\Frost\AppData\Roaming\PrismLauncher\instances\1.21.1 Minigames\minecraft\mods\",
        "C:\Users\Frost\AppData\Roaming\PrismLauncher\instances\1.21.1 Minigames 2\minecraft\mods\"
    )

    foreach ($dest in $destinations) {
        Remove-Item "$dest\miniverse-*-sources.jar" -ErrorAction SilentlyContinue
        Copy-Item "build\libs\miniverse-1.0.0.jar" -Destination $dest -Force
    }
    
    Write-Host "Deployment complete!" -ForegroundColor Cyan
} else {
    Write-Host "Build failed! Fix the errors before deploying." -ForegroundColor Red
}
