Write-Host "============================================="
Write-Host " COURSE REGISTRATION SYSTEM HEALTH CHECK"
Write-Host "============================================="

$allGood = $true

Write-Host "`n[1] Checking Docker Containers..."

$containers = docker ps --format "{{.Names}}"

foreach ($name in @("seat1","course1","auth1","autoscaler")) {
    if ($containers -match $name) {
        Write-Host "✔ $name is running"
    } else {
        Write-Host "✘ $name is NOT running"
        $allGood = $false
    }
}

Write-Host "`n[2] Checking Service Chain..."

$response = ""
try { $response = iwr http://localhost:8080/login -Method POST -UseBasicParsing } catch {}

if ($response -and $response.Content -eq "REGISTRATION_COMPLETE") {
    Write-Host "✔ Auth → Course → Seat chain working"
} else {
    Write-Host "✘ Login endpoint failed"
    $allGood = $false
}

Write-Host "`n[3] Checking Prometheus..."

$promHealthy = $false
try { iwr http://localhost:9090/-/healthy -UseBasicParsing | Out-Null; $promHealthy = $true } catch {}

if ($promHealthy) {
    Write-Host "✔ Prometheus running"
} else {
    Write-Host "✘ Prometheus not reachable"
    $allGood = $false
}

Write-Host "`n[4] Checking Metrics..."

$metricCheck = ""
try { $metricCheck = iwr "http://localhost:9090/api/v1/query?query=http_server_requests_seconds_max" -UseBasicParsing } catch {}

if ($metricCheck -and $metricCheck.Content -match "result") {
    Write-Host "✔ Metrics available"
} else {
    Write-Host "✘ Metrics not available"
    $allGood = $false
}

Write-Host "`n============================================="

if ($allGood) {
    Write-Host " SYSTEM STATUS: HEALTHY ✔"
} else {
    Write-Host " SYSTEM STATUS: ISSUES DETECTED ✘"
}

Write-Host "============================================="