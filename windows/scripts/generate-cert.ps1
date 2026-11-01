# Generate Self-Signed Certificate for CleanTV Local Sideloading
$ErrorActionPreference = "Stop"

$certSubject = "CN=CleanTV"
Write-Host "Creating self-signed code signing certificate for '$certSubject'..."

$cert = New-SelfSignedCertificate `
    -Type Custom `
    -Subject $certSubject `
    -KeyUsage DigitalSignature `
    -FriendlyName "CleanTV MSIX Developer Certificate" `
    -CertStoreLocation "Cert:\CurrentUser\My" `
    -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3")

Write-Host "Certificate generated successfully."
Write-Host "Thumbprint: $($cert.Thumbprint)"
Write-Host "Subject:    $($cert.Subject)"
Write-Host "`nTo trust this cert for local sideloading, install it into 'Trusted People' store:"
Write-Host "  Export-Certificate -Cert $cert -FilePath CleanTV.cer"
Write-Host "  Import-Certificate -FilePath CleanTV.cer -CertStoreLocation Cert:\LocalMachine\TrustedPeople"
