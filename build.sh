#!/bin/bash

# Burp SQLMap Extension Build Script for Linux/Mac

echo "========================================"
echo "Burp SQLMap Extension Builder"
echo "========================================"
echo ""

# Check if javac is available
if ! command -v javac &> /dev/null; then
    echo "Error: javac not found. Please install JDK."
    exit 1
fi

# Check if jar command is available
if ! command -v jar &> /dev/null; then
    echo "Error: jar command not found. Please install JDK."
    exit 1
fi

# Find Burp Suite JAR
BURP_JAR=""

# Check common locations
if [ -f "burpsuite_pro.jar" ]; then
    BURP_JAR="burpsuite_pro.jar"
elif [ -f "../burpsuite_pro.jar" ]; then
    BURP_JAR="../burpsuite_pro.jar"
elif [ -f "$HOME/BurpSuitePro/burpsuite_pro.jar" ]; then
    BURP_JAR="$HOME/BurpSuitePro/burpsuite_pro.jar"
fi

if [ -z "$BURP_JAR" ]; then
    echo "Warning: burpsuite_pro.jar not found automatically."
    echo "Please ensure burpsuite_pro.jar is in the current directory"
    echo "or provide the path when prompted."
    echo ""
    read -p "Enter path to burpsuite_pro.jar (or press Enter to use 'burpsuite_pro.jar'): " USER_BURP_JAR
    
    if [ -n "$USER_BURP_JAR" ]; then
        BURP_JAR="$USER_BURP_JAR"
    else
        BURP_JAR="burpsuite_pro.jar"
    fi
fi

echo "Using Burp JAR: $BURP_JAR"
echo ""

# Check if Burp JAR exists
if [ ! -f "$BURP_JAR" ]; then
    echo "Error: Burp Suite JAR not found at: $BURP_JAR"
    echo ""
    echo "To get the Burp API JAR:"
    echo "1. Open Burp Suite Professional"
    echo "2. Go to Extender > APIs"
    echo "3. Click 'Save interface files'"
    echo "4. Save to this directory as 'burpsuite_pro.jar'"
    exit 1
fi

# Clean previous build
echo "Cleaning previous build..."
rm -rf burp/
rm -f sqlmap-extension.jar

# Create package directory
mkdir -p burp

# Compile
echo "Compiling SqlmapExtension.java..."
javac -cp "$BURP_JAR:." -d . SqlmapExtension.java

if [ $? -ne 0 ]; then
    echo ""
    echo "Compilation failed!"
    exit 1
fi

# Create JAR
echo "Creating JAR file..."
jar -cf sqlmap-extension.jar burp/*.class

if [ $? -ne 0 ]; then
    echo ""
    echo "JAR creation failed!"
    exit 1
fi

# Clean up class files
echo "Cleaning up..."
rm -rf burp/

echo ""
echo "========================================"
echo "Build successful!"
echo "========================================"
echo ""
echo "Output: sqlmap-extension.jar"
echo ""
echo "To install in Burp Suite:"
echo "1. Open Burp Suite Professional"
echo "2. Go to Extender > Extensions"
echo "3. Click 'Add'"
echo "4. Select 'sqlmap-extension.jar'"
echo "5. Click 'Next'"
echo ""
echo "Extension will appear in the 'SQLMap' tab."
