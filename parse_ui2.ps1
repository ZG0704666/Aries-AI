$content = Get-Content '.sisyphus\evidence\final-qa\ui_settings2.xml' -Raw
# Find all clickable nodes with text
$pattern = 'clickable="true"[^>]*bounds="([^"]+)"'
$matches2 = [regex]::Matches($content, 'text="([^"]+)"[^/]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"')
foreach($m in $matches2) {
    $txt = $m.Groups[1].Value
    $bounds = $m.Groups[2].Value
    if ($txt -ne "") {
        Write-Output "$txt | $bounds"
    }
}
