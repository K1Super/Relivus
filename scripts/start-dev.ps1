<#
.SYNOPSIS
  Relivus 一键启动脚本——同时启动后端（8080）与前端（5173）。

.DESCRIPTION
  读取项目根目录 .env 中的 RELIVUS_* 配置并注入后端进程环境变量，然后：
  1. 后端：backend/target/relivus.jar（缺失时自动 mvn 构建）
  2. 前端：frontend 目录执行 npm run dev（vite，/api 代理到 8080）
  前后端各开独立控制台窗口，便于分别查看日志。

.PARAMETER SkipBuild
  跳过后端构建，直接使用已有 relivus.jar（jar 缺失或过期时勿用）。

.PARAMETER NoFrontend
  只启动后端，不起前端。

.EXAMPLE
  .\scripts\start-dev.ps1
  .\scripts\start-dev.ps1 -SkipBuild
  .\scripts\start-dev.ps1 -NoFrontend
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$NoFrontend
)

$ErrorActionPreference = 'Stop'

# ---- 定位项目根目录与关键路径 ----
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
$backendDir = Join-Path $root 'backend'
$frontendDir = Join-Path $root 'frontend'
$jarFile = Join-Path $backendDir 'target\relivus.jar'

if (-not (Test-Path $envFile)) {
    Write-Host "未找到 $envFile 。" -ForegroundColor Red
    Write-Host "请先执行：Copy-Item `"$(Join-Path $root '.env.example')`" $envFile" -ForegroundColor Yellow
    Write-Host "然后填写元数据库密码与密钥后重试。密钥生成：" -ForegroundColor Yellow
    Write-Host '  openssl rand -base64 32   （RELIVUS_AES_KEY 与 RELIVUS_HMAC_KEY 各生成一个）' -ForegroundColor Yellow
    Write-Host '  openssl rand -hex 16      （RELIVUS_TOKEN）' -ForegroundColor Yellow
    exit 1
}

# ---- 解析 .env：KEY=VALUE，跳过空行与 # 注释，注入当前会话 ----
$required = @('RELIVUS_META_URL', 'RELIVUS_META_USER', 'RELIVUS_META_PASSWORD',
              'RELIVUS_AES_KEY', 'RELIVUS_HMAC_KEY', 'RELIVUS_TOKEN')
$loaded = @{}
Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $idx = $line.IndexOf('=')
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    $loaded[$key] = $value
}

# ---- 必填校验（对齐后端 StartupConfigValidator）----
$missing = $required | Where-Object {
    -not $loaded.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($loaded[$_]) -or $loaded[$_] -like '<*>*'
}
if ($missing) {
    Write-Host "以下必需变量缺失、仍为占位符或为默认占位密码：$($missing -join ', ')" -ForegroundColor Red
    exit 1
}

# ---- 密钥格式预校验：Base64 解码后须为 32 字节（AES-256 / HMAC）----
foreach ($k in 'RELIVUS_AES_KEY', 'RELIVUS_HMAC_KEY') {
    try {
        if ([Convert]::FromBase64String($loaded[$k]).Length -ne 32) {
            Write-Host "$k 解码后长度不是 32 字节（AES-256/HMAC 要求）。" -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "$k 不是合法 Base64。请用 openssl rand -base64 32 重新生成。" -ForegroundColor Red
        exit 1
    }
}

Write-Host "读取 $envFile 成功，共 $($loaded.Keys.Count) 个配置项。" -ForegroundColor Green

# ---- 构建后端（如需要）----
if (-not $SkipBuild -and -not (Test-Path $jarFile)) {
    Write-Host 'relivus.jar 不存在，开始构建（跳过测试）...' -ForegroundColor Cyan
    Push-Location $backendDir
    try {
        & mvn -q clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "mvn package 失败，退出码 $LASTEXITCODE" }
    } finally { Pop-Location }
}

# ---- 前端依赖检查 ----
if (-not $NoFrontend) {
    if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
        Write-Host 'frontend/node_modules 不存在，执行 npm ci ...' -ForegroundColor Cyan
        Push-Location $frontendDir
        try {
            & npm ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci 失败，退出码 $LASTEXITCODE" }
        } finally { Pop-Location }
    }
}

# ---- 启动后端（新窗口，注入 .env 环境变量）----
$backendScript = @"
`$env:RELIVUS_META_URL = '$($loaded['RELIVUS_META_URL'])'
`$env:RELIVUS_META_USER = '$($loaded['RELIVUS_META_USER'])'
`$env:RELIVUS_META_PASSWORD = '$($loaded['RELIVUS_META_PASSWORD'])'
`$env:RELIVUS_AES_KEY = '$($loaded['RELIVUS_AES_KEY'])'
`$env:RELIVUS_HMAC_KEY = '$($loaded['RELIVUS_HMAC_KEY'])'
`$env:RELIVUS_TOKEN = '$($loaded['RELIVUS_TOKEN'])'
Set-Location '$backendDir'
Write-Host '[backend] Relivus 后端启动中...' -ForegroundColor Cyan
java -jar '$jarFile'
"@
$backendCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($backendScript))
Start-Process powershell -ArgumentList '-NoProfile', '-EncodedCommand', $backendCommand -WorkingDirectory $backendDir
Write-Host '后端已在独立窗口启动：http://127.0.0.1:8080' -ForegroundColor Green

# ---- 启动前端（新窗口）----
if (-not $NoFrontend) {
    $frontendScript = @"
Set-Location '$frontendDir'
Write-Host '[frontend] Relivus 前端启动中，浏览器访问 http://localhost:5173' -ForegroundColor Cyan
npm run dev
"@
    $frontendCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($frontendScript))
    Start-Process powershell -ArgumentList '-NoProfile', '-EncodedCommand', $frontendCommand -WorkingDirectory $frontendDir
    Write-Host '前端已在独立窗口启动：http://localhost:5173' -ForegroundColor Green
    Write-Host '' 
    Write-Host '浏览器打开 http://localhost:5173，在设置页填入 RELIVUS_TOKEN 后即可使用。' -ForegroundColor Yellow
}

Write-Host ''
Write-Host '停止服务：运行 scripts\stop-all.ps1，或直接关闭对应窗口。' -ForegroundColor Yellow