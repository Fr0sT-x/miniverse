./gradlew build

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build successful! Copying to server and clients..." -ForegroundColor Green
    
    # Copy to Server
    Copy-Item "build\libs\miniverse-*.jar" -Destination "C:\Users\Frost\Desktop\Server 1.21.1\mods\" -Force
    
    # Copy to Client
    Copy-Item "build\libs\miniverse-*.jar" -Destination "C:\Users\Frost\AppData\Roaming\PrismLauncher\instances\Fabulously Optimized(2)\minecraft\mods\" -Force
    
    Write-Host "Deployment complete!" -ForegroundColor Cyan
} else {
    Write-Host "Build failed! Fix the errors before deploying." -ForegroundColor Red
}
