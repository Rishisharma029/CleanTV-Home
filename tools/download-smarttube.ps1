[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$url = "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_beta.apk"
$dest = "apks/smarttube-latest.apk"
Write-Output "Downloading SmartTube to $dest..."
try {
  $client = New-Object System.Net.WebClient
  $client.Headers.Add("User-Agent", "Mozilla/5.0")
  $client.DownloadFile($url, $dest)
  $size = (Get-Item $dest).Length
  Write-Output "Downloaded successfully: $size bytes"
} catch {
  Write-Output "Error: $($_.Exception.Message)"
}
