$xml = [xml](Get-Content '.sisyphus\evidence\final-qa\ui_dump.xml')
$nodes = $xml.SelectNodes('//*[@text != ""]')
foreach($n in $nodes) {
    Write-Output ($n.text + ' | ' + $n.bounds)
}
