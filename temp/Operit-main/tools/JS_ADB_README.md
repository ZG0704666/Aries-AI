# ADB JavaScript Executor

This tool allows you to push JavaScript files to Android devices via ADB and remotely execute specific functions.

## Features

- Push JavaScript files to Android devices using ADB
- Execute specified JavaScript functions
- Support for JSON parameter passing
- Temporary file option (auto-deletion after execution)
- Implementations in Shell script, Windows Batch file, and Python script
- **Device selection for multi-device setups**

## Prerequisites

- Android SDK (ADB)
- Android device with USB debugging enabled
- ADB debugging permission granted on the device
- Your application installed on the device

## Quick Start

### Using Shell Script (Linux/macOS)

1. Make the script executable:
```bash
chmod +x execute_js.sh
```

2. Execute the script:
```bash
./execute_js.sh path/to/your/script.js functionName '{"param1":"value1"}'
```

### Using Batch Script (Windows)

1. Execute the batch file:
```cmd
execute_js.bat path\to\your\script.js functionName "{\"param1\":\"value1\"}"
```

### Using Python Script (Cross-platform)

1. Ensure Python 3 is installed

2. Execute the script:
```bash
python execute_js.py path/to/your/script.js functionName --params '{"param1":"value1"}'
```

3. Additional options:
```bash
# Specify a device directly (skip device selection prompt)
python execute_js.py path/to/script.js functionName --device DEVICE_SERIAL

# Keep the file on the device after execution
python execute_js.py path/to/script.js functionName --keep
```

## Device Selection

When multiple devices are connected:

1. The script will display a list of available devices
2. Enter the number corresponding to your target device
3. All ADB operations will be performed on the selected device

Example:
```
Checking connected devices...
1: emulator-5554
2: 192.168.1.100:5555
Enter device number (1-2): 
```

## Examples

### 1. Execute the greeting function

```bash
# Linux/macOS
./execute_js.sh test_script.js sayHello '{"name":"John"}'

# Windows
execute_js.bat test_script.js sayHello "{\"name\":\"John\"}"

# Python (any platform)
python execute_js.py test_script.js sayHello --params '{"name":"John"}'
```

### 2. Execute the calculation function

```bash
# Linux/macOS
./execute_js.sh test_script.js calculate '{"num1":10,"num2":5,"operation":"multiply"}'

# Windows
execute_js.bat test_script.js calculate "{\"num1\":10,\"num2\":5,\"operation\":\"multiply\"}"

# Python (any platform)
python execute_js.py test_script.js calculate --params '{"num1":10,"num2":5,"operation":"multiply"}'
```

## Viewing Execution Results

You can view the execution logs using:

```bash
adb logcat -s ScriptExecutionReceiver JsEngine
```

## JavaScript File Requirements

- Files must be valid JavaScript (TypeScript not supported)
- Functions must be exported (using `exports.functionName = functionName`)
- Functions must accept a params parameter (containing the passed parameters)
- Functions should use the `complete(result)` function to return results

## Required Function Example

```javascript
function myFunction(params) {
    // Process parameters
    const name = params.name || "default";
    
    // Execute business logic
    const result = `Result: ${name}`;
    
    // Return result
    complete({
        success: true,
        result: result
    });
}

// Export function
exports.myFunction = myFunction;
```

## Technical Details

1. The Shell/Batch/Python script pushes the JavaScript file to a temporary directory on the device
2. An ADB broadcast is sent to the application with the execution request
3. The application's BroadcastReceiver receives the request and uses JsEngine to execute the function
4. Execution results are written to the Android logs

## Troubleshooting

### Common Issues

1. **ADB command not found**  
   Ensure the Android SDK's platform-tools directory is in your PATH environment variable.

2. **No devices detected**
   - Make sure the device is connected
   - Ensure USB debugging is enabled
   - Check that the computer is authorized for USB debugging

3. **Broadcast sent but function not executed**
   - Check the logs for error messages
   - Confirm the application is running
   - Verify the receiver is correctly registered

4. **JavaScript errors**
   - Check for TypeScript-specific syntax (like type annotations)
   - Ensure functions are properly exported
   - Verify parameter formats are correct
   
## Required Permissions

This feature requires the following permissions:

- Read and write external storage
- Permission to receive broadcasts

## Security Considerations

- This feature should only be used for development and debugging purposes
- In production environments, this feature should be disabled or restricted to prevent security risks 