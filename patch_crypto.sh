sed -i 's/Log.e(TAG, "File decryption failed: ", e)/Log.e(TAG, "File decryption failed: ", e)\n            outputFile.delete()/g' app/src/main/java/com/example/util/CryptoUtils.kt
sed -i 's/Log.e(TAG, "File encryption failed: ", e)/Log.e(TAG, "File encryption failed: ", e)\n            outputFile.delete()/g' app/src/main/java/com/example/util/CryptoUtils.kt
