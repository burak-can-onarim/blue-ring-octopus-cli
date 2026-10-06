<#
.SYNOPSIS
    Creates a "Blue Ring Octopus CLI" shortcut (with the application icon) that starts start-agent.bat.

.PARAMETER OutputDir
    Folder for the shortcut. Defaults to your Desktop.
#>
param(
    [string]$OutputDir = [Environment]::GetFolderPath('Desktop')
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $root 'start-agent.bat'
$icon = Join-Path $root 'src\main\resources\icons\blue-ring-octopus.ico'
$jar = Join-Path $root 'target\blue-ring-octopus-cli.jar'

foreach ($required in @($launcher, $icon)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing file: $required"
    }
}
if (-not (Test-Path -LiteralPath $jar)) {
    Write-Warning "target\blue-ring-octopus-cli.jar was not found. Build it first: mvnw.cmd -DskipTests package"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$path = Join-Path $OutputDir 'Blue Ring Octopus CLI.lnk'

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($path)
$shortcut.TargetPath = $launcher
$shortcut.WorkingDirectory = $root
$shortcut.IconLocation = "$icon,0"
$shortcut.Description = 'Blue Ring Octopus CLI - local AI code analysis'
$shortcut.Save()

Write-Host "Shortcut created: $path"
