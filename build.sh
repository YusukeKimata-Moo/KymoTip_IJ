#!/bin/bash
# Build script for KymoTip_IJ/CCN Registration Fiji plugins
# Usage: bash build.sh /path/to/Fiji.app
#
# Example:
#   bash build.sh "C:/Users/ysk-m/Desktop/Fiji"

FIJI_DIR="${1:-C:/Users/ysk-m/Desktop/Fiji}"
IJ_JAR="$FIJI_DIR/jars/ij-*.jar"
JTS_JAR="$FIJI_DIR/jars/jts-core-1.20.0.jar"
MATH_JAR="$FIJI_DIR/jars/commons-math3-3.6.1.jar"

# Find ij.jar
IJ=$(ls $IJ_JAR 2>/dev/null | head -1)
if [ -z "$IJ" ]; then
    echo "ERROR: Cannot find ij.jar in $FIJI_DIR/jars/"
    exit 1
fi

echo "Using ImageJ jar: $IJ"
echo "Using JTS jar: $JTS_JAR"

# Compile
mkdir -p build
# In bash on Windows, path separator for java cp is typically ; but inside quotes
javac -cp "${IJ};${JTS_JAR};${MATH_JAR}" -d build src/CCN_Registration.java src/KymoTip_Centerline.java src/KymoTip_Trajectory.java
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

# Copy plugins.config into build
cp plugins.config build/

# Bundle JTS classes into build (fat jar)
cd build
jar xf "$JTS_JAR"
cd ..

# Create JAR
cd build
jar cf ../KymoTip_IJ.jar plugins.config *.class org/
cd ..

echo ""
echo "Built: KymoTip_IJ.jar"
echo "Copy it to: $FIJI_DIR/plugins/"
echo "Then restart Fiji. The plugins appear under Plugins menu."
