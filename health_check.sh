#!/bin/bash

# VVF Smart File Manager – Health Check Script
# यह स्क्रिप्ट 4 चरणों में कोड की जाँच करती है:

echo "🧪 Starting Health Check..."

# 1. Clean build (पुरानी बिल्ड फाइलें हटाएँ)
echo "🧹 Step 1: Cleaning project..."
./gradlew clean
if [ $? -ne 0 ]; then
    echo "❌ Clean failed! Check your Gradle setup."
    exit 1
fi

# 2. Full Debug Build (APK बनाएँ)
echo "🔨 Step 2: Building debug APK..."
./gradlew assembleDebug
if [ $? -ne 0 ]; then
    echo "❌ Build failed! Check compilation errors."
    exit 1
fi

# 3. Unit Tests (JVM tests चलाएँ)
echo "🧪 Step 3: Running unit tests..."
./gradlew testDebugUnitTest
if [ $? -ne 0 ]; then
    echo "❌ Unit tests failed! Fix test failures."
    exit 1
fi

# 4. Lint Check (स्टैटिक विश्लेषण)
echo "🔍 Step 4: Running lint check..."
./gradlew lint
if [ $? -ne 0 ]; then
    echo "❌ Lint issues found! Check the reports."
    exit 1
fi

echo "✅ All checks passed! Your code is healthy."
