#!/bin/bash
# Build script for CCN Registration Fiji plugin
# Usage: bash build.sh /path/to/Fiji.app
#
# Example:
#   bash build.sh "C:/Users/ysk-m/Desktop/Fiji"

FIJI_DIR="${1:-C:/Users/ysk-m/Desktop/Fiji}"
IJ_JAR="$FIJI_DIR/jars/ij-*.jar"

# Find ij.jar
IJ=$(ls $IJ_JAR 2>/dev/null | head -1)
if [ -z "$IJ" ]; then
    echo "ERROR: Cannot find ij.jar in $FIJI_DIR/jars/"
    exit 1
fi

echo "Using ImageJ jar: $IJ"

# Compile
mkdir -p build
javac -cp "$IJ" -d build src/CCN_Registration.java
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

# Copy plugins.config into build
cp plugins.config build/

# Create JAR
cd build
jar cf ../CCN_Registration.jar plugins.config CCN_Registration.class
cd ..

echo ""
echo "Built: CCN_Registration.jar"
echo "Copy it to: $FIJI_DIR/plugins/"
echo "Then restart Fiji. The plugin appears under Plugins > CCN > CCN Registration"
