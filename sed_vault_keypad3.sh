sed -i 's/Icons.Filled.LockReset/Icons.Filled.Fingerprint/g' /app/applet/app/src/main/java/com/example/ui/screens/VaultScreen.kt
sed -i 's/contentDescription = "Reset PIN"/contentDescription = "Biometric Unlock"/g' /app/applet/app/src/main/java/com/example/ui/screens/VaultScreen.kt
