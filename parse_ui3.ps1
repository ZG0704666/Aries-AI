$content = Get-Content '.sisyphus\evidence\final-qa\ui_set3.xml' -Raw
# Find all clickable items with their bounds
$pattern = 'clickable="true"[^>]*?bounds="(\[\d+,\d+\]\[\d+,\d+\])"'
$matches2 = [regex]::Matches($content, $pattern)
foreach($m in $matches2) {
    Write-Output $m.Groups[1].Value
}
