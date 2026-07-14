<#
.SYNOPSIS
  Sync the canonical /docs/ directory into Aries-site/docs/ for the docs-center website.

.DESCRIPTION
  Aries-site/docs-center.html fetches markdown files from Aries-site/docs/ at runtime.
  To avoid tracking 107 duplicate files in git, Aries-site/docs/ is gitignored and
  treated as a build artifact. Run this script before deploying the site, or after
  editing files under /docs/, to refresh the copy.

.NOTES
  The canonical source is the repo-root /docs/ directory. This script mirrors it
  into Aries-site/docs/ preserving the directory structure.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$source = Join-Path $repoRoot 'docs'
$destination = Join-Path $repoRoot 'Aries-site\docs'

if (-not (Test-Path $source)) {
    throw "Source directory not found: $source"
}

if (Test-Path $destination) {
    Remove-Item -Recurse -Force $destination
}

New-Item -ItemType Directory -Path $destination -Force | Out-Null

Copy-Item -Path "$source\*" -Destination $destination -Recurse -Force

$copiedCount = (Get-ChildItem $destination -Recurse -File).Count
Write-Host "Synced $copiedCount files from $source to $destination" -ForegroundColor Green
