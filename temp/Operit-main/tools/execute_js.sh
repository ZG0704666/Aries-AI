#!/bin/bash

# Check parameters
if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <JS_file_path> <function_name> [parameters_JSON]"
    echo "Example: $0 ./example.js hello_world '{\"name\":\"John\"}'"
    exit 1
fi

# Parameters
FILE_PATH="$1"
FUNCTION_NAME="$2"
PARAMS="${3:-{}}"  # Default to empty object

# Check if file exists
if [ ! -f "$FILE_PATH" ]; then
    echo "Error: File does not exist - $FILE_PATH"
    exit 1
fi

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "Error: ADB command not found. Make sure Android SDK is installed and adb is in PATH"
    exit 1
fi

# Check for connected devices and handle device selection
echo "Checking connected devices..."
# Filter to include only lines ending with "device" (fully connected devices)
DEVICE_LIST=($(adb devices | grep -v "List" | grep "device$" | awk '{print $1}'))
DEVICE_COUNT=${#DEVICE_LIST[@]}

if [ $DEVICE_COUNT -eq 0 ]; then
    echo "Error: No Android devices detected"
    exit 1
fi

# Handle device selection
if [ $DEVICE_COUNT -eq 1 ]; then
    DEVICE_SERIAL=${DEVICE_LIST[0]}
    echo "Using the only connected device: $DEVICE_SERIAL"
else
    echo "Multiple devices detected. Please select a device:"
    for i in $(seq 0 $((DEVICE_COUNT-1))); do
        echo "$((i+1)): ${DEVICE_LIST[$i]}"
    done
    
    echo -n "Enter device number (1-$DEVICE_COUNT): "
    read SELECTION
    
    # Validate selection
    if ! [[ "$SELECTION" =~ ^[0-9]+$ ]] || [ $SELECTION -lt 1 ] || [ $SELECTION -gt $DEVICE_COUNT ]; then
        echo "Error: Invalid selection"
        exit 1
    fi
    
    # Array is 0-indexed but user selection is 1-indexed
    DEVICE_SERIAL=${DEVICE_LIST[$((SELECTION-1))]}
    echo "Using selected device: $DEVICE_SERIAL"
fi

# Create target directory
TARGET_DIR="/sdcard/Android/data/com.ai.assistance.operit/js_temp"
echo "Creating directory on device..."
adb -s "$DEVICE_SERIAL" shell "mkdir -p $TARGET_DIR" 2>/dev/null

# Check if directory creation succeeded, try alternative methods if not
if [ $? -ne 0 ]; then
    echo "Standard mkdir failed, trying alternative method..."
    adb -s "$DEVICE_SERIAL" shell "mkdir /sdcard/Android/data/com.ai.assistance.operit" 2>/dev/null
    adb -s "$DEVICE_SERIAL" shell "mkdir /sdcard/Android/data/com.ai.assistance.operit/js_temp" 2>/dev/null
fi

# Get file name and target path
FILE_NAME=$(basename "$FILE_PATH")
TARGET_FILE="$TARGET_DIR/$FILE_NAME"

# Push file to device
echo "Pushing $FILE_PATH to device $DEVICE_SERIAL..."
adb -s "$DEVICE_SERIAL" push "$FILE_PATH" "$TARGET_FILE"

if [ $? -ne 0 ]; then
    echo "Error: Failed to push file"
    exit 1
fi

# Escape JSON parameters 
ESCAPED_PARAMS=$(echo "$PARAMS" | sed 's/"/\\"/g')

# Send broadcast to execute script
echo "Executing function $FUNCTION_NAME on device $DEVICE_SERIAL..."
adb -s "$DEVICE_SERIAL" shell am broadcast -a com.ai.assistance.operit.EXECUTE_JS \
    -n com.ai.assistance.operit/.tools.javascript.ScriptExecutionReceiver \
    --include-stopped-packages \
    --es file_path "$TARGET_FILE" \
    --es function_name "$FUNCTION_NAME" \
    --es params "$ESCAPED_PARAMS" \
    --ez temp_file true

echo "Waiting for execution to complete..."
sleep 2

echo "Capturing logcat output for JsEngine tag (Press Ctrl+C to stop):"
adb -s "$DEVICE_SERIAL" logcat -s JsEngine:* 