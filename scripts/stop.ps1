<#
.SYNOPSIS
  停止 Relivus 前后端服务。

.DESCRIPTION
  按端口停止后端（8080）与前端（5173）的监听进程。
  用法： .\scripts\stop-all.ps1
#>
$ErrorActionPreference = 'SilentlyContinue'

foreach ($port in 8080, 5173) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        $procIds = $conn | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $procIds) {
            Stop-Process -Id $procId -Force
            Write-Host "[stop] 端口 $port 进程 PID=$procId 已结束" -ForegroundColor Green
        }
    } else {
        Write-Host "[info] 端口 $port 无监听进程" -ForegroundColor Gray
    }
}
Write-Host 'Relivus 服务已停止。' -ForegroundColor Green