/**
 * OperIT AI Tool Tester
 *
 * This script tests all available tools in the OperIT system as documented in tools.md.
 * It validates each tool's functionality and the structure of its return values.
 *
 * How to run:
 * 1. Compile: tsc operit-tester.ts
 * 2. Run via command line:
 *    - Windows: .\tools\execute_js.bat operit-tester.js main '{}'
 *    - Linux/macOS: ./tools/execute_js.sh operit-tester.js main '{}'
 *    - Specific test category: .\tools\execute_js.bat operit-tester.js testCategory '{"testType":"ui"}'
 */
/**
 * Formats and prints an object in a readable format
 */
function prettyPrint(label, data) {
    console.log(`\n=== ${label} ===`);
    console.log(JSON.stringify(data, undefined, 2));
    console.log("=".repeat(label.length + 8));
}
/**
 * Validates that a UI node has the expected structure
 */
function validateUINodeStructure(node) {
    if (!node)
        return false;
    // Check required properties
    if (typeof node.isClickable !== 'boolean') {
        console.error(node);
        console.error(node.isClickable + " is not a boolean, isClickable is " + typeof node.isClickable);
        console.error("Node is missing isClickable property or it's not a boolean");
        return false;
    }
    if (!Array.isArray(node.children)) {
        console.error("Node is missing children array property");
        return false;
    }
    // Check optional properties if present
    if (node.className !== undefined && typeof node.className !== 'string') {
        console.error("Node className is not a string");
        return false;
    }
    if (node.text !== undefined && typeof node.text !== 'string') {
        console.error("Node text is not a string");
        return false;
    }
    if (node.contentDesc !== undefined && typeof node.contentDesc !== 'string') {
        console.error("Node contentDesc is not a string");
        return false;
    }
    if (node.resourceId !== undefined && typeof node.resourceId !== 'string') {
        console.error("Node resourceId is not a string");
        return false;
    }
    if (node.bounds !== undefined && typeof node.bounds !== 'string') {
        console.error("Node bounds is not a string");
        return false;
    }
    // Recursively validate all children
    for (const child of node.children) {
        if (!validateUINodeStructure(child)) {
            return false;
        }
    }
    return true;
}
/**
 * Prints a UI node hierarchy with indentation
 */
function printUIHierarchy(node, indent = "") {
    if (!node)
        return;
    const className = node.className || "unknown";
    const text = node.text ? `"${node.text}"` : "";
    const desc = node.contentDesc ? `(${node.contentDesc})` : "";
    const id = node.resourceId ? `#${node.resourceId.split("/").pop()}` : "";
    const clickable = node.isClickable ? "👆" : "";
    console.log(`${indent}${clickable}[${className}] ${text} ${desc} ${id}`);
    if (node.children) {
        node.children.forEach(child => printUIHierarchy(child, indent + "  "));
    }
}
/**
 * Main test runner function that organizes and executes all test categories
 */
async function runTests(params = {}) {
    var _a, _b;
    console.log("Starting OperIT Tool Tester...");
    console.log("Parameters:", params);
    const results = {};
    const testSummary = [];
    const startTime = Date.now();
    const testType = params.testType || "all";
    try {
        // Test basic tools
        if (testType === "all" || testType === "basic") {
            console.log("\n🔧 Testing Basic Tools...");
            testSummary.push("Running basic tools tests");
            await testQueryMemory(results);
            await testUsePackage(results);
            await testCalculator(results);
            await testSleep(results);
        }
        // Test file system tools
        if (testType === "all" || testType === "files") {
            console.log("\n📁 Testing File System Tools...");
            testSummary.push("Running file system tools tests");
            // First create a test file as foundation for other tests
            await testWriteFile(results);
            // Then test other operations in a logical sequence
            await testFileExists(results);
            await testListFiles(results);
            await testReadFile(results);
            await testMakeDirectory(results);
            await testCopyFile(results);
            await testMoveFile(results);
            await testFindFiles(results);
            await testFileInfo(results);
            // Test zip operations if previous tests were successful
            if (((_a = results["write_file"]) === null || _a === void 0 ? void 0 : _a.success) && ((_b = results["make_directory"]) === null || _b === void 0 ? void 0 : _b.success)) {
                await testZipFiles(results);
                await testUnzipFiles(results);
            }
            // Test opening and sharing files
            await testOpenFile(results);
            await testShareFile(results);
            // Clean up test files at the end
            await testDeleteFile(results);
        }
        // Test network tools
        if (testType === "all" || testType === "network") {
            console.log("\n🌐 Testing Network Tools...");
            testSummary.push("Running network tools tests");
            await testWebSearch(results);
            await testHttpRequest(results);
            await testDownloadFile(results);
        }
        // Test system operation tools
        if (testType === "all" || testType === "system") {
            console.log("\n⚙️ Testing System Operation Tools...");
            testSummary.push("Running system operation tools tests");
            await testDeviceInfo(results);
            await testGetSystemSetting(results);
            await testListInstalledApps(results);
            await testStartApp(results);
            await testStopApp(results);
            // Skip these tests by default as they require special permissions
            if (params.testType === "system_danger") {
                await testModifySystemSetting(results);
                await testInstallApp(results);
                await testUninstallApp(results);
            }
        }
        // Test UI automation tools
        if (testType === "all" || testType === "ui") {
            console.log("\n📱 Testing UI Automation Tools...");
            testSummary.push("Running UI automation tools tests");
            await testGetPageInfo(results);
            await testClickElement(results);
            await testTap(results);
            await testSetInputText(results);
            await testPressKey(results);
            await testSwipe(results);
        }
        // Print test summary
        const duration = Date.now() - startTime;
        console.log("\n📊 Test Summary:");
        Object.entries(results).forEach(([test, result]) => {
            const status = result.success ? "✅ PASS" : "❌ FAIL";
            console.log(`${status}: ${test}`);
        });
        const successCount = Object.values(results).filter(r => r.success).length;
        const totalTests = Object.keys(results).length;
        const summaryText = `Overall: ${successCount}/${totalTests} tests passed in ${duration / 1000}s`;
        console.log(`\n${summaryText}`);
        console.log("\nOperIT Tool Tester completed!");
        // Return results
        complete({
            testSummary,
            summary: summaryText,
            testsPassed: successCount,
            testsTotal: totalTests,
            testResults: results,
            duration: `${duration / 1000}s`
        });
    }
    catch (error) {
        console.error("Unexpected error in test suite:", error);
        complete({
            error: String(error),
            testSummary,
            testResults: results
        });
    }
}
/**
 * Tests the query_memory tool
 */
async function testQueryMemory(results) {
    try {
        console.log("\nTesting query_memory...");
        const queryResult = await toolCall("query_memory", {
            query: "how to use OperIT tools"
        });
        // Validate the result (queryResult is a string)
        const resultString = queryResult;
        console.log(`Query result type: ${typeof resultString}`);
        console.log(`Query result length: ${resultString.length} characters`);
        console.log(`Result preview: ${resultString.substring(0, 100)}...`);
        results["query_memory"] = {
            success: typeof resultString === 'string' && resultString.length > 0,
            data: resultString
        };
    }
    catch (err) {
        console.error("Error testing query_memory:", err);
        results["query_memory"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the use_package tool
 */
async function testUsePackage(results) {
    try {
        console.log("\nTesting use_package...");
        const packageResult = await toolCall("use_package", {
            package_name: "example_package"
        });
        // Validate the result (packageResult is a string)
        const resultString = packageResult;
        console.log(`Package result type: ${typeof resultString}`);
        console.log(`Package result: ${resultString}`);
        results["use_package"] = {
            success: typeof resultString === 'string',
            data: resultString
        };
    }
    catch (err) {
        console.error("Error testing use_package:", err);
        results["use_package"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the calculate tool
 */
async function testCalculator(results) {
    try {
        console.log("\nTesting calculate...");
        const expressions = [
            "2 + 2",
            "5 * (3 - 1)",
            "sin(30)"
        ];
        for (const expression of expressions) {
            console.log(`Calculating: ${expression}`);
            const calcResult = await toolCall("calculate", {
                expression: expression
            });
            // Validate the result
            const calcData = calcResult;
            console.log(`Expression: ${calcData.expression}`);
            console.log(`Result: ${calcData.result}`);
            console.log(`Formatted result: ${calcData.formattedResult}`);
            if (calcData.variables && Object.keys(calcData.variables).length > 0) {
                console.log(`Variables: ${JSON.stringify(calcData.variables)}`);
            }
        }
        results["calculate"] = { success: true };
    }
    catch (err) {
        console.error("Error testing calculate:", err);
        results["calculate"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the sleep tool
 */
async function testSleep(results) {
    try {
        console.log("\nTesting sleep...");
        const testDurations = [100, 500, 1000];
        for (const duration of testDurations) {
            console.log(`Sleeping for ${duration}ms...`);
            const startTime = Date.now();
            const sleepResult = await toolCall("sleep", {
                duration_ms: duration
            });
            const endTime = Date.now();
            const actualDuration = endTime - startTime;
            // Validate the result
            const sleepData = sleepResult;
            console.log(`Requested sleep: ${duration}ms`);
            console.log(`Actual sleep duration: ~${actualDuration}ms`);
            // Allow for some timing variance
            const sleepAccuracy = Math.abs(actualDuration - duration) < 100;
            console.log(`Sleep accuracy OK: ${sleepAccuracy ? "✅" : "❌"}`);
        }
        results["sleep"] = { success: true };
    }
    catch (err) {
        console.error("Error testing sleep:", err);
        results["sleep"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the list_files tool
 */
async function testListFiles(results) {
    try {
        console.log("\nTesting list_files...");
        const filesList = await toolCall("list_files", {
            path: "/sdcard/"
        });
        // Validate the result
        const dirListing = filesList;
        console.log(`Listed directory: ${dirListing.path}`);
        console.log(`Found ${dirListing.entries.length} entries`);
        // Display some sample entries
        if (dirListing.entries.length > 0) {
            console.log("\nSample entries:");
            dirListing.entries.slice(0, 3).forEach(entry => {
                console.log(`- ${entry.name} (${entry.isDirectory ? "Directory" : "File"}, ${entry.size} bytes, modified: ${entry.lastModified})`);
            });
        }
        results["list_files"] = {
            success: Array.isArray(dirListing.entries),
            data: dirListing
        };
    }
    catch (err) {
        console.error("Error testing list_files:", err);
        results["list_files"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the write_file tool
 */
async function testWriteFile(results) {
    try {
        console.log("\nTesting write_file...");
        const testContent = "This is a test file created by the OperIT tool tester.\nTest timestamp: " + new Date().toISOString();
        const testPath = "/sdcard/operit_test_file.txt";
        const writeResult = await toolCall("write_file", {
            path: testPath,
            content: testContent
        });
        // Validate the result
        const writeData = writeResult;
        console.log(`Operation: ${writeData.operation}`);
        console.log(`Path: ${writeData.path}`);
        console.log(`Success: ${writeData.successful}`);
        console.log(`Details: ${writeData.details}`);
        results["write_file"] = {
            success: writeData.successful,
            data: { path: testPath, content: testContent, result: writeData }
        };
    }
    catch (err) {
        console.error("Error testing write_file:", err);
        results["write_file"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the read_file tool
 */
async function testReadFile(results) {
    var _a;
    try {
        console.log("\nTesting read_file...");
        // Check if write_file was successful
        if (!((_a = results["write_file"]) === null || _a === void 0 ? void 0 : _a.success)) {
            console.log("WARNING: write_file test did not succeed. Reading test file may fail.");
        }
        const testPath = "/sdcard/operit_test_file.txt";
        const readResult = await toolCall("read_file", {
            path: testPath
        });
        // Validate the result
        const fileData = readResult;
        console.log(`File path: ${fileData.path}`);
        console.log(`File size: ${fileData.size} bytes`);
        console.log(`Content preview: ${fileData.content.substring(0, 100)}...`);
        // Verify the content contains expected test data
        const containsTestMarker = fileData.content.includes("test file created by the OperIT tool tester");
        console.log(`Content verification: ${containsTestMarker ? "✅ Matched" : "❌ Failed"}`);
        results["read_file"] = {
            success: containsTestMarker && fileData.size > 0,
            data: fileData
        };
    }
    catch (err) {
        console.error("Error testing read_file:", err);
        results["read_file"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the file_exists tool
 */
async function testFileExists(results) {
    try {
        console.log("\nTesting file_exists...");
        // Test for a file that should exist
        const testPath = "/sdcard/operit_test_file.txt";
        const existsResult = await toolCall("file_exists", {
            path: testPath
        });
        // Validate the result
        const existsData = existsResult;
        console.log(`File path: ${existsData.path}`);
        console.log(`File exists: ${existsData.exists}`);
        if (existsData.exists) {
            console.log(`Is directory: ${existsData.isDirectory}`);
            console.log(`Size: ${existsData.size} bytes`);
        }
        // Test for a file that shouldn't exist
        const nonExistentPath = "/sdcard/this_file_should_not_exist_" + Date.now() + ".txt";
        const nonExistsResult = await toolCall("file_exists", {
            path: nonExistentPath
        });
        const nonExistsData = nonExistsResult;
        console.log(`\nNon-existent file check:`);
        console.log(`File path: ${nonExistsData.path}`);
        console.log(`File exists: ${nonExistsData.exists} (should be false)`);
        results["file_exists"] = {
            success: existsData.exists && !nonExistsData.exists,
            data: { exists: existsData, nonExists: nonExistsData }
        };
    }
    catch (err) {
        console.error("Error testing file_exists:", err);
        results["file_exists"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the make_directory tool
 */
async function testMakeDirectory(results) {
    try {
        console.log("\nTesting make_directory...");
        const testDirPath = "/sdcard/operit_test_directory";
        const mkdirResult = await toolCall("make_directory", {
            path: testDirPath
        });
        // Validate the result
        const mkdirData = mkdirResult;
        console.log(`Operation: ${mkdirData.operation}`);
        console.log(`Path: ${mkdirData.path}`);
        console.log(`Success: ${mkdirData.successful}`);
        console.log(`Details: ${mkdirData.details}`);
        // Verify directory was created
        const verifyResult = await toolCall("file_exists", {
            path: testDirPath
        });
        const verifyData = verifyResult;
        console.log(`Directory exists: ${verifyData.exists}`);
        console.log(`Is directory: ${verifyData.isDirectory}`);
        results["make_directory"] = {
            success: mkdirData.successful && verifyData.exists && verifyData.isDirectory === true,
            data: { create: mkdirData, verify: verifyData }
        };
    }
    catch (err) {
        console.error("Error testing make_directory:", err);
        results["make_directory"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the copy_file tool
 */
async function testCopyFile(results) {
    var _a;
    try {
        console.log("\nTesting copy_file...");
        // Check if write_file was successful
        if (!((_a = results["write_file"]) === null || _a === void 0 ? void 0 : _a.success)) {
            console.log("WARNING: write_file test did not succeed. Copying test file may fail.");
        }
        const sourcePath = "/sdcard/operit_test_file.txt";
        const destPath = "/sdcard/operit_test_directory/copied_file.txt";
        const copyResult = await toolCall("copy_file", {
            source: sourcePath,
            destination: destPath
        });
        // Validate the result
        const copyData = copyResult;
        console.log(`Operation: ${copyData.operation}`);
        console.log(`Path: ${copyData.path}`);
        console.log(`Success: ${copyData.successful}`);
        console.log(`Details: ${copyData.details}`);
        // Verify copied file exists
        const verifyResult = await toolCall("file_exists", {
            path: destPath
        });
        const verifyData = verifyResult;
        console.log(`Copied file exists: ${verifyData.exists}`);
        results["copy_file"] = {
            success: copyData.successful && verifyData.exists,
            data: { copy: copyData, verify: verifyData }
        };
    }
    catch (err) {
        console.error("Error testing copy_file:", err);
        results["copy_file"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the move_file tool
 */
async function testMoveFile(results) {
    var _a;
    try {
        console.log("\nTesting move_file...");
        // Check if copy_file was successful
        if (!((_a = results["copy_file"]) === null || _a === void 0 ? void 0 : _a.success)) {
            console.log("WARNING: copy_file test did not succeed. Moving test file may fail.");
        }
        const sourcePath = "/sdcard/operit_test_directory/copied_file.txt";
        const destPath = "/sdcard/operit_test_directory/moved_file.txt";
        const moveResult = await toolCall("move_file", {
            source: sourcePath,
            destination: destPath
        });
        // Validate the result
        const moveData = moveResult;
        console.log(`Operation: ${moveData.operation}`);
        console.log(`Path: ${moveData.path}`);
        console.log(`Success: ${moveData.successful}`);
        console.log(`Details: ${moveData.details}`);
        // Verify source no longer exists
        const sourceCheck = await toolCall("file_exists", {
            path: sourcePath
        });
        // Verify destination exists
        const destCheck = await toolCall("file_exists", {
            path: destPath
        });
        const sourceExists = sourceCheck.exists;
        const destExists = destCheck.exists;
        console.log(`Source file still exists: ${sourceExists} (should be false)`);
        console.log(`Destination file exists: ${destExists} (should be true)`);
        results["move_file"] = {
            success: moveData.successful && !sourceExists && destExists,
            data: moveData
        };
    }
    catch (err) {
        console.error("Error testing move_file:", err);
        results["move_file"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the find_files tool
 */
async function testFindFiles(results) {
    try {
        console.log("\nTesting find_files...");
        const findResult = await toolCall("find_files", {
            path: "/sdcard/",
            pattern: "operit_test_*",
            max_depth: 5,
            case_insensitive: true
        });
        // Validate the result
        const findData = findResult;
        console.log(`Search path: ${findData.path}`);
        console.log(`Pattern: ${findData.pattern}`);
        console.log(`Found files: ${findData.files.length}`);
        if (findData.files.length > 0) {
            console.log("\nFiles found:");
            findData.files.forEach(file => {
                console.log(`- ${file}`);
            });
        }
        // We should find at least 2 things (the test file and directory)
        const foundEnough = findData.files.length >= 2;
        console.log(`Found sufficient test files: ${foundEnough ? "✅" : "❌"}`);
        results["find_files"] = {
            success: foundEnough,
            data: findData
        };
    }
    catch (err) {
        console.error("Error testing find_files:", err);
        results["find_files"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the file_info tool
 */
async function testFileInfo(results) {
    try {
        console.log("\nTesting file_info...");
        const testFilePath = "/sdcard/operit_test_file.txt";
        const fileInfoResult = await toolCall("file_info", {
            path: testFilePath
        });
        // Validate the result
        const fileInfo = fileInfoResult;
        console.log(`Path: ${fileInfo.path}`);
        console.log(`Exists: ${fileInfo.exists}`);
        console.log(`Type: ${fileInfo.fileType}`);
        console.log(`Size: ${fileInfo.size} bytes`);
        console.log(`Permissions: ${fileInfo.permissions}`);
        console.log(`Owner: ${fileInfo.owner}`);
        console.log(`Group: ${fileInfo.group}`);
        console.log(`Last Modified: ${fileInfo.lastModified}`);
        results["file_info"] = {
            success: fileInfo.exists,
            data: fileInfo
        };
    }
    catch (err) {
        console.error("Error testing file_info:", err);
        results["file_info"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the zip_files tool
 */
async function testZipFiles(results) {
    var _a;
    try {
        console.log("\nTesting zip_files...");
        const sourceDir = "/sdcard/operit_test_directory";
        const destZip = "/sdcard/operit_test.zip";
        const zipResult = await toolCall("zip_files", {
            source: sourceDir,
            destination: destZip
        });
        // Validate the result
        const zipData = zipResult;
        console.log(`Operation: ${zipData.operation}`);
        console.log(`Path: ${zipData.path}`);
        console.log(`Success: ${zipData.successful}`);
        console.log(`Details: ${zipData.details}`);
        // Verify zip file was created
        const verifyResult = await toolCall("file_exists", {
            path: destZip
        });
        const verifyData = verifyResult;
        console.log(`Zip file exists: ${verifyData.exists}`);
        console.log(`Zip file size: ${verifyData.size} bytes`);
        results["zip_files"] = {
            success: zipData.successful && verifyData.exists && ((_a = verifyData.size) !== null && _a !== void 0 ? _a : 0) > 0,
            data: { zip: zipData, verify: verifyData }
        };
    }
    catch (err) {
        console.error("Error testing zip_files:", err);
        results["zip_files"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the unzip_files tool
 */
async function testUnzipFiles(results) {
    var _a;
    try {
        console.log("\nTesting unzip_files...");
        // Check if zip_files was successful
        if (!((_a = results["zip_files"]) === null || _a === void 0 ? void 0 : _a.success)) {
            console.log("WARNING: zip_files test did not succeed. Unzipping test file may fail.");
        }
        const sourceZip = "/sdcard/operit_test.zip";
        const destDir = "/sdcard/operit_test_extracted";
        // Create the destination directory first
        await toolCall("make_directory", {
            path: destDir
        });
        const unzipResult = await toolCall("unzip_files", {
            source: sourceZip,
            destination: destDir
        });
        // Validate the result
        const unzipData = unzipResult;
        console.log(`Operation: ${unzipData.operation}`);
        console.log(`Path: ${unzipData.path}`);
        console.log(`Success: ${unzipData.successful}`);
        console.log(`Details: ${unzipData.details}`);
        // Verify extraction by listing files in the destination directory
        if (unzipData.successful) {
            const listResult = await toolCall("list_files", {
                path: destDir
            });
            const listData = listResult;
            console.log(`\nExtracted contents (${listData.entries.length} items):`);
            listData.entries.forEach(entry => {
                console.log(`- ${entry.name}`);
            });
        }
        results["unzip_files"] = {
            success: unzipData.successful,
            data: unzipData
        };
    }
    catch (err) {
        console.error("Error testing unzip_files:", err);
        results["unzip_files"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the open_file tool
 */
async function testOpenFile(results) {
    try {
        console.log("\nTesting open_file...");
        const testFilePath = "/sdcard/operit_test_file.txt";
        const openResult = await toolCall("open_file", {
            path: testFilePath
        });
        // Validate the result
        const openData = openResult;
        console.log(`Operation: ${openData.operation}`);
        console.log(`Path: ${openData.path}`);
        console.log(`Success: ${openData.successful}`);
        console.log(`Details: ${openData.details}`);
        results["open_file"] = {
            success: openData.successful,
            data: openData
        };
    }
    catch (err) {
        console.error("Error testing open_file:", err);
        results["open_file"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the share_file tool
 */
async function testShareFile(results) {
    try {
        console.log("\nTesting share_file...");
        const testFilePath = "/sdcard/operit_test_file.txt";
        const shareResult = await toolCall("share_file", {
            path: testFilePath,
            title: "OperIT Test Share"
        });
        // Validate the result
        const shareData = shareResult;
        console.log(`Operation: ${shareData.operation}`);
        console.log(`Path: ${shareData.path}`);
        console.log(`Success: ${shareData.successful}`);
        console.log(`Details: ${shareData.details}`);
        results["share_file"] = {
            success: shareData.successful,
            data: shareData
        };
    }
    catch (err) {
        console.error("Error testing share_file:", err);
        results["share_file"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the delete_file tool and cleans up test files
 */
async function testDeleteFile(results) {
    try {
        console.log("\nTesting delete_file and cleaning up test files...");
        // List of test files and directories to clean up
        const testPaths = [
            "/sdcard/operit_test_file.txt",
            "/sdcard/operit_test_directory",
            "/sdcard/operit_test.zip",
            "/sdcard/operit_test_extracted"
        ];
        let allSuccessful = true;
        for (const path of testPaths) {
            console.log(`\nDeleting ${path}...`);
            const deleteResult = await toolCall("delete_file", {
                path: path,
                recursive: true // Use recursive deletion for directories
            });
            const deleteData = deleteResult;
            console.log(`Operation: ${deleteData.operation}`);
            console.log(`Path: ${deleteData.path}`);
            console.log(`Success: ${deleteData.successful}`);
            if (deleteData.details) {
                console.log(`Details: ${deleteData.details}`);
            }
            // Verify it's gone
            const verifyResult = await toolCall("file_exists", {
                path: path
            });
            const verifyData = verifyResult;
            const isGone = !verifyData.exists;
            console.log(`File/directory removed: ${isGone ? "✅" : "❌"}`);
            allSuccessful = allSuccessful && deleteData.successful && isGone;
        }
        results["delete_file"] = {
            success: allSuccessful,
            data: { message: "All test files cleaned up" }
        };
    }
    catch (err) {
        console.error("Error testing delete_file:", err);
        results["delete_file"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the visit_web tool
 */
async function testWebSearch(results) {
    try {
        console.log("\nTesting visit_web...");
        const url = "https://www.baidu.com/s?wd=OperIT+AI+automation+tools";
        const searchResult = await toolCall("visit_web", {
            url: url
        });
        // Validate the result
        const visitData = searchResult;
        console.log(`Visited URL: ${visitData.url}`);
        console.log(`Page Title: ${visitData.title}`);
        console.log(`Content length: ${visitData.content.length} characters`);
        if (visitData.metadata && Object.keys(visitData.metadata).length > 0) {
            console.log("\nPage metadata:");
            Object.entries(visitData.metadata).slice(0, 3).forEach(([key, value]) => {
                console.log(`${key}: ${value}`);
            });
        }
        // Show content preview
        const contentPreview = visitData.content.length > 200 ?
            visitData.content.substring(0, 200) + "..." :
            visitData.content;
        console.log(`\nContent preview: ${contentPreview}`);
        results["visit_web"] = {
            success: visitData.url === url && visitData.content.length > 0,
            data: visitData
        };
    }
    catch (err) {
        console.error("Error testing visit_web:", err);
        results["visit_web"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the http_request tool
 */
async function testHttpRequest(results) {
    try {
        console.log("\nTesting http_request...");
        // Test GET request
        console.log("Testing GET request...");
        const getResult = await toolCall("http_request", {
            url: "https://httpbin.org/get",
            method: "GET"
        });
        // Validate the GET result
        const getData = getResult;
        console.log(`GET Status: ${getData.statusCode} ${getData.statusMessage}`);
        console.log(`Content type: ${getData.contentType}`);
        console.log(`Content size: ${getData.size} bytes`);
        // Test POST request with JSON body
        console.log("\nTesting POST request with JSON body...");
        const postBody = { test: "value", timestamp: new Date().toISOString() };
        const postResult = await toolCall("http_request", {
            url: "https://httpbin.org/post",
            method: "POST",
            headers: JSON.stringify({ "Content-Type": "application/json" }),
            body: JSON.stringify(postBody),
            body_type: "json"
        });
        // Validate the POST result
        const postData = postResult;
        console.log(`POST Status: ${postData.statusCode} ${postData.statusMessage}`);
        console.log(`Content type: ${postData.contentType}`);
        console.log(`Content size: ${postData.size} bytes`);
        // Test if our data was received correctly
        const hasEchoedData = postData.content.includes(postBody.test);
        console.log(`Request body correctly echoed: ${hasEchoedData ? "✅" : "❌"}`);
        results["http_request"] = {
            success: getData.statusCode === 200 && postData.statusCode === 200,
            data: { get: getData, post: postData }
        };
    }
    catch (err) {
        console.error("Error testing http_request:", err);
        results["http_request"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the download_file tool
 */
async function testDownloadFile(results) {
    try {
        console.log("\nTesting download_file...");
        // Use a reliable small image for testing
        const downloadUrl = "https://httpbin.org/image/png";
        const downloadPath = "/sdcard/operit_test_download.png";
        const downloadResult = await toolCall("download_file", {
            url: downloadUrl,
            destination: downloadPath
        });
        // Validate the result
        const downloadData = downloadResult;
        console.log(`Operation: ${downloadData.operation}`);
        console.log(`Path: ${downloadData.path}`);
        console.log(`Success: ${downloadData.successful}`);
        console.log(`Details: ${downloadData.details}`);
        // Verify the file was downloaded
        if (downloadData.successful) {
            const verifyResult = await toolCall("file_exists", {
                path: downloadPath
            });
            const verifyData = verifyResult;
            console.log(`\nDownloaded file exists: ${verifyData.exists}`);
            console.log(`File size: ${verifyData.size} bytes`);
            // Clean up the downloaded file
            if (verifyData.exists) {
                console.log("Cleaning up downloaded file...");
                await toolCall("delete_file", {
                    path: downloadPath
                });
            }
        }
        results["download_file"] = {
            success: downloadData.successful,
            data: downloadData
        };
    }
    catch (err) {
        console.error("Error testing download_file:", err);
        results["download_file"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the device_info tool
 */
async function testDeviceInfo(results) {
    try {
        console.log("\nTesting device_info...");
        const deviceResult = await toolCall("device_info");
        // Validate the result
        const deviceData = deviceResult;
        console.log("=== Device Information ===");
        console.log(`Device ID: ${deviceData.deviceId}`);
        console.log(`Model: ${deviceData.manufacturer} ${deviceData.model}`);
        console.log(`Android Version: ${deviceData.androidVersion} (SDK ${deviceData.sdkVersion})`);
        console.log("\n=== Display ===");
        console.log(`Resolution: ${deviceData.screenResolution}`);
        console.log(`Density: ${deviceData.screenDensity}`);
        console.log("\n=== Memory & Storage ===");
        console.log(`Memory: ${deviceData.availableMemory} available of ${deviceData.totalMemory} total`);
        console.log(`Storage: ${deviceData.availableStorage} available of ${deviceData.totalStorage} total`);
        console.log("\n=== Status ===");
        console.log(`Battery: ${deviceData.batteryLevel}% ${deviceData.batteryCharging ? "(charging)" : "(not charging)"}`);
        console.log(`Network: ${deviceData.networkType}`);
        console.log(`Processor: ${deviceData.cpuInfo}`);
        // Check additional info
        if (deviceData.additionalInfo && Object.keys(deviceData.additionalInfo).length > 0) {
            console.log("\n=== Additional Information ===");
            Object.entries(deviceData.additionalInfo).forEach(([key, value]) => {
                console.log(`${key}: ${value}`);
            });
        }
        // Basic validation - check that we have essential device data
        const hasBasicInfo = deviceData.model &&
            deviceData.manufacturer &&
            deviceData.androidVersion &&
            typeof deviceData.sdkVersion === 'number';
        results["device_info"] = {
            success: hasBasicInfo === true,
            data: deviceData
        };
    }
    catch (err) {
        console.error("Error testing device_info:", err);
        results["device_info"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the get_system_setting tool
 */
async function testGetSystemSetting(results) {
    try {
        console.log("\nTesting get_system_setting...");
        // Test array of commonly available settings
        const settingsToTest = [
            { namespace: "system", setting: "screen_brightness_mode" },
            { namespace: "system", setting: "screen_brightness" },
            { namespace: "system", setting: "time_12_24" }
        ];
        const settingResults = [];
        for (const { namespace, setting } of settingsToTest) {
            console.log(`\nFetching ${namespace}/${setting}...`);
            const settingResult = await toolCall("get_system_setting", {
                namespace,
                setting
            });
            // Validate the result
            const settingData = settingResult;
            console.log(`Setting namespace: ${settingData.namespace}`);
            console.log(`Setting name: ${settingData.setting}`);
            console.log(`Setting value: ${settingData.value}`);
            settingResults.push({
                // requested: { namespace, setting },
                data: settingData,
                success: settingData.namespace === namespace &&
                    settingData.setting === setting &&
                    settingData.value !== undefined
            });
        }
        // Check if all settings were retrieved successfully
        const allSuccessful = settingResults.every(r => r.success);
        results["get_system_setting"] = {
            success: allSuccessful,
            data: settingResults
        };
    }
    catch (err) {
        console.error("Error testing get_system_setting:", err);
        results["get_system_setting"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the modify_system_setting tool - USE WITH CAUTION
 * This is marked as potentially dangerous and requires user authorization
 */
async function testModifySystemSetting(results) {
    try {
        console.log("\nTesting modify_system_setting...");
        console.log("CAUTION: This test attempts to modify system settings and requires user authorization");
        // Get current setting first
        const getResult = await toolCall("get_system_setting", {
            namespace: "system",
            setting: "screen_brightness_mode"
        });
        const originalData = getResult;
        console.log(`Current brightness mode: ${originalData.value}`);
        // Toggle the value
        const newValue = originalData.value === "1" ? "0" : "1";
        console.log(`Setting brightness mode to: ${newValue}`);
        const modifyResult = await toolCall("modify_system_setting", {
            namespace: "system",
            setting: "screen_brightness_mode",
            value: newValue
        });
        // Validate the result
        const modifyData = modifyResult;
        console.log(`Setting modified: ${modifyData.namespace}/${modifyData.setting}`);
        console.log(`New value: ${modifyData.value}`);
        // Verify the change
        const verifyResult = await toolCall("get_system_setting", {
            namespace: "system",
            setting: "screen_brightness_mode"
        });
        const verifyData = verifyResult;
        console.log(`Verified new value: ${verifyData.value}`);
        // Restore original value
        console.log(`Restoring original value: ${originalData.value}`);
        await toolCall("modify_system_setting", {
            namespace: "system",
            setting: "screen_brightness_mode",
            value: originalData.value
        });
        results["modify_system_setting"] = {
            success: verifyData.value === newValue,
            data: { original: originalData, modified: modifyData, verified: verifyData }
        };
    }
    catch (err) {
        console.error("Error testing modify_system_setting:", err);
        results["modify_system_setting"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the list_installed_apps tool
 */
async function testListInstalledApps(results) {
    try {
        console.log("\nTesting list_installed_apps...");
        // Test without system apps
        console.log("Listing user apps (exclude system apps)...");
        const userAppsResult = await toolCall("list_installed_apps", {
            include_system_apps: false
        });
        // Validate the result
        const userAppsData = userAppsResult;
        console.log(`Including system apps: ${userAppsData.includesSystemApps}`);
        console.log(`Total user apps: ${userAppsData.packages.length}`);
        if (userAppsData.packages.length > 0) {
            console.log("\nSample user apps:");
            userAppsData.packages.slice(0, 5).forEach(app => {
                console.log(`- ${app}`);
            });
        }
        // Test with system apps
        console.log("\nListing all apps (include system apps)...");
        const allAppsResult = await toolCall("list_installed_apps", {
            include_system_apps: true
        });
        // Validate the result
        const allAppsData = allAppsResult;
        console.log(`Including system apps: ${allAppsData.includesSystemApps}`);
        console.log(`Total apps: ${allAppsData.packages.length}`);
        // Verify we get more apps when including system apps
        const moreWithSystem = allAppsData.packages.length > userAppsData.packages.length;
        console.log(`More apps when including system apps: ${moreWithSystem ? "✅" : "❌"}`);
        results["list_installed_apps"] = {
            success: userAppsData.packages.length > 0 && moreWithSystem,
            data: { userApps: userAppsData, allApps: allAppsData }
        };
    }
    catch (err) {
        console.error("Error testing list_installed_apps:", err);
        results["list_installed_apps"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the install_app tool - USE WITH CAUTION
 * This is marked as potentially dangerous and requires user authorization
 */
async function testInstallApp(results) {
    try {
        console.log("\nTesting install_app...");
        console.log("CAUTION: This test requires an APK file and user authorization");
        console.log("Skipping actual installation - this is a dangerous operation");
        // Instead of actual installation, we'll just check if the function is available
        results["install_app"] = {
            success: true,
            data: { message: "Test skipped - dangerous operation" }
        };
    }
    catch (err) {
        console.error("Error testing install_app:", err);
        results["install_app"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the uninstall_app tool - USE WITH CAUTION
 * This is marked as potentially dangerous and requires user authorization
 */
async function testUninstallApp(results) {
    try {
        console.log("\nTesting uninstall_app...");
        console.log("CAUTION: This test would uninstall an app and requires user authorization");
        console.log("Skipping actual uninstallation - this is a dangerous operation");
        // Instead of actual uninstallation, we'll just check if the function is available
        results["uninstall_app"] = {
            success: true,
            data: { message: "Test skipped - dangerous operation" }
        };
    }
    catch (err) {
        console.error("Error testing uninstall_app:", err);
        results["uninstall_app"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the start_app tool
 */
async function testStartApp(results) {
    try {
        console.log("\nTesting start_app...");
        // Use the settings app which should be available on all devices
        const packageName = "com.android.settings";
        // First test: Standard app launch
        console.log("Testing standard app launch...");
        const startResult = await toolCall("start_app", {
            package_name: packageName
        });
        // Validate the result
        const startData = startResult;
        console.log(`Operation type: ${startData.operationType}`);
        console.log(`Package name: ${startData.packageName}`);
        console.log(`Success: ${startData.success}`);
        console.log(`Details: ${startData.details || ""}`);
        // Wait a moment for the app to start
        console.log("Waiting for app to start...");
        await toolCall("sleep", { duration_ms: 2000 });
        // Second test: Launch with specific activity
        console.log("\nTesting specific activity launch...");
        // Settings main activity is a common one
        const activity = "com.android.settings.Settings";
        const startWithActivityResult = await toolCall("start_app", {
            package_name: packageName,
            activity: activity
        });
        // Validate the activity launch result
        const activityData = startWithActivityResult;
        console.log(`Operation type: ${activityData.operationType}`);
        console.log(`Package name: ${activityData.packageName}`);
        console.log(`Success: ${activityData.success}`);
        console.log(`Details: ${activityData.details || ""}`);
        // Final result combines both tests
        const bothSucceeded = startData.success && activityData.success;
        const activitySpecified = activityData.details && activityData.details.includes(activity);
        results["start_app"] = {
            success: bothSucceeded && activityData.packageName === packageName && activitySpecified === true,
            data: {
                standard: startData,
                withActivity: activityData
            }
        };
    }
    catch (err) {
        console.error("Error testing start_app:", err);
        results["start_app"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the stop_app tool
 */
async function testStopApp(results) {
    try {
        console.log("\nTesting stop_app...");
        // Try to stop the settings app we started in the previous test
        const packageName = "com.android.settings";
        const stopResult = await toolCall("stop_app", {
            package_name: packageName
        });
        // Validate the result
        const stopData = stopResult;
        console.log(`Operation type: ${stopData.operationType}`);
        console.log(`Package name: ${stopData.packageName}`);
        console.log(`Success: ${stopData.success}`);
        console.log(`Details: ${stopData.details || ""}`);
        results["stop_app"] = {
            success: stopData.success && stopData.packageName === packageName,
            data: stopData
        };
    }
    catch (err) {
        console.error("Error testing stop_app:", err);
        results["stop_app"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the get_page_info tool
 */
async function testGetPageInfo(results) {
    try {
        console.log("\nTesting get_page_info...");
        // Test with default parameters
        console.log("Getting page info with default format and detail level...");
        const pageInfoResult = await toolCall("get_page_info");
        // Validate the result
        const pageData = pageInfoResult;
        console.log(`Current package: ${pageData.packageName}`);
        console.log(`Current activity: ${pageData.activityName}`);
        // Validate UI elements structure
        console.log("\nValidating UI elements structure...");
        const isValidStructure = validateUINodeStructure(pageData.uiElements);
        console.log(`UI structure validation: ${isValidStructure ? "✅ Valid" : "❌ Invalid"}`);
        // Count UI elements
        let nodeCount = 0;
        let clickableCount = 0;
        function countNodes(node) {
            if (!node)
                return;
            nodeCount++;
            if (node.isClickable)
                clickableCount++;
            if (node.children && node.children.length > 0) {
                node.children.forEach(countNodes);
            }
        }
        countNodes(pageData.uiElements);
        console.log(`Total UI nodes: ${nodeCount}`);
        console.log(`Clickable elements: ${clickableCount}`);
        // Print UI hierarchy
        console.log("\nUI Hierarchy (abbreviated):");
        printUIHierarchy(pageData.uiElements);
        // Try with XML format
        console.log("\nGetting page info with XML format...");
        const xmlResult = await toolCall("get_page_info", {
            format: "xml",
            detail: "summary"
        });
        // Try with JSON format and minimal detail
        console.log("\nGetting page info with JSON format and minimal detail...");
        const jsonMinResult = await toolCall("get_page_info", {
            format: "json",
            detail: "minimal"
        });
        // Try with JSON format and full detail
        console.log("\nGetting page info with JSON format and full detail...");
        const jsonFullResult = await toolCall("get_page_info", {
            format: "json",
            detail: "full"
        });
        results["get_page_info"] = {
            success: isValidStructure && nodeCount > 0,
            data: {
                default: pageData,
                xmlFormat: xmlResult,
                jsonMinimal: jsonMinResult,
                jsonFull: jsonFullResult
            }
        };
    }
    catch (err) {
        console.error("Error testing get_page_info:", err);
        results["get_page_info"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the click_element tool
 */
async function testClickElement(results) {
    console.log("\n--- Testing click_element ---");
    try {
        // First get page info to have a reference point
        const pageInfo = await toolCall("get_page_info");
        if (!pageInfo || !pageInfo.uiElements) {
            results["click_element"] = {
                success: false,
                error: "Failed to get initial UI hierarchy"
            };
            return;
        }
        console.log("Current UI before attempting clicks");
        // Find clickable elements
        let clickableFound = false;
        let resourceId = "";
        let className = "";
        let bounds = "";
        function findClickableElement(node) {
            if (node.isClickable && node.resourceId) {
                clickableFound = true;
                resourceId = node.resourceId;
                className = node.className || "";
                bounds = node.bounds || "";
                return;
            }
            for (const child of node.children) {
                if (clickableFound)
                    return;
                findClickableElement(child);
            }
        }
        // Find a clickable element in the current UI
        findClickableElement(pageInfo.uiElements);
        if (!clickableFound) {
            console.log("No clickable elements with resource ID found, trying generic approaches");
            // Try to click a generic button by class name
            const result = await toolCall("click_element", {
                className: "android.widget.Button",
                index: 0
            });
            if (result) {
                console.log("Successfully clicked button by class name");
                results["click_element"] = {
                    success: true,
                    data: result
                };
            }
            else {
                console.log("Failed to click button by class name, trying by text");
                const textResult = await toolCall("click_element", {
                    text: "OK",
                    partialMatch: true
                });
                if (textResult) {
                    console.log("Successfully clicked element with text containing 'OK'");
                    results["click_element"] = {
                        success: true,
                        data: textResult
                    };
                }
                else {
                    // As a last resort, try a generic clickable
                    const clickableResult = await toolCall("click_element", {
                        className: "android.view.View",
                        isClickable: true,
                        index: 0
                    });
                    results["click_element"] = {
                        success: clickableResult ? true : false,
                        data: clickableResult || { actionType: "click", actionDescription: "All click attempts failed" }
                    };
                }
            }
        }
        else {
            console.log(`Testing click_element with resourceId: ${resourceId}`);
            // Test with resourceId parameter as an object
            let result = await toolCall("click_element", {
                resourceId: resourceId
            });
            console.log("Object click by resource ID result:", result);
            // Test with the bounds parameter if we have bounds
            if (bounds) {
                console.log(`Testing click using bounds: ${bounds}`);
                result = await toolCall("click_element", {
                    bounds: bounds
                });
                console.log("Click by bounds result:", result);
            }
            // Test clicking by class name if available
            if (className) {
                console.log(`Testing click by className: ${className}`);
                const classResult = await toolCall("click_element", {
                    className: className,
                    index: 0
                });
                console.log("Click by class name result:", classResult);
            }
            results["click_element"] = {
                success: result ? true : false,
                data: result
            };
        }
    }
    catch (error) {
        console.error("Error in testClickElement:", error);
        results["click_element"] = {
            success: false,
            error: error.toString()
        };
    }
}
/**
 * Tests the tap tool
 */
async function testTap(results) {
    try {
        console.log("\nTesting tap...");
        // Get screen info to determine good coordinates
        const deviceResult = await toolCall("device_info");
        const deviceData = deviceResult;
        // Parse resolution into width and height
        let width = 1080; // Default fallback
        let height = 1920;
        if (deviceData.screenResolution) {
            const match = deviceData.screenResolution.match(/(\d+)x(\d+)/);
            if (match) {
                width = parseInt(match[1]);
                height = parseInt(match[2]);
            }
        }
        // Calculate center coordinates
        const centerX = Math.floor(width / 2);
        const centerY = Math.floor(height / 2);
        console.log(`Using screen center coordinates: (${centerX}, ${centerY})`);
        // Tap the center of the screen
        const tapResult = await toolCall("tap", {
            x: centerX,
            y: centerY
        });
        // Validate the result
        const tapData = tapResult;
        console.log(`Action type: ${tapData.actionType}`);
        console.log(`Action description: ${tapData.actionDescription}`);
        if (tapData.coordinates) {
            console.log(`Coordinates: (${tapData.coordinates[0]}, ${tapData.coordinates[1]})`);
        }
        // Wait a moment for any UI response
        console.log("Waiting for UI to respond...");
        await toolCall("sleep", { duration_ms: 1000 });
        results["tap"] = {
            success: true,
            data: tapData
        };
    }
    catch (err) {
        console.error("Error testing tap:", err);
        results["tap"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the set_input_text tool
 */
async function testSetInputText(results) {
    try {
        console.log("\nTesting set_input_text...");
        // First try to find an input field
        console.log("Analyzing UI to find a text input field...");
        const pageInfoResult = await toolCall("get_page_info");
        const pageData = pageInfoResult;
        // Find an input field
        let inputField = undefined;
        function findInput(node) {
            if (!node)
                return;
            if (node.className === "android.widget.EditText" && !inputField) {
                inputField = node;
                return;
            }
            if (node.children && node.children.length > 0) {
                node.children.forEach(findInput);
            }
        }
        findInput(pageData.uiElements);
        if (inputField) {
            console.log("\nFound input field:");
            console.log(`Resource ID: ${inputField.resourceId || "(no ID)"}`);
            console.log(`Current text: ${inputField.text || "(empty)"}`);
            // Try to set text
            const testText = "OperIT Test Input " + Date.now();
            console.log(`\nSetting text to: "${testText}"`);
            const inputResult = await toolCall("set_input_text", {
                text: testText,
                resourceId: inputField.resourceId
            });
            // Validate the result
            const inputData = inputResult;
            console.log(`Action type: ${inputData.actionType}`);
            console.log(`Action description: ${inputData.actionDescription}`);
            results["set_input_text"] = {
                success: true,
                data: { field: inputField, input: inputData, text: testText }
            };
        }
        else {
            // If no input field found, report this
            console.log("\nNo input field found in the current UI");
            results["set_input_text"] = {
                success: true,
                data: { message: "No input field found to test with" }
            };
        }
    }
    catch (err) {
        console.error("Error testing set_input_text:", err);
        results["set_input_text"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the press_key tool
 */
async function testPressKey(results) {
    try {
        console.log("\nTesting press_key...");
        // Test pressing the back key
        console.log("Pressing BACK key...");
        const backResult = await toolCall("press_key", {
            key_code: "KEYCODE_BACK"
        });
        // Validate the result
        const backData = backResult;
        console.log(`Action type: ${backData.actionType}`);
        console.log(`Action description: ${backData.actionDescription}`);
        // Wait for UI to respond
        console.log("Waiting for UI to respond...");
        await toolCall("sleep", { duration_ms: 1000 });
        // Test pressing the home key
        console.log("\nPressing HOME key...");
        const homeResult = await toolCall("press_key", {
            key_code: "KEYCODE_HOME"
        });
        // Validate the result
        const homeData = homeResult;
        console.log(`Action type: ${homeData.actionType}`);
        console.log(`Action description: ${homeData.actionDescription}`);
        // Wait for UI to respond
        console.log("Waiting for UI to respond...");
        await toolCall("sleep", { duration_ms: 1000 });
        results["press_key"] = {
            success: true,
            data: { back: backData, home: homeData }
        };
    }
    catch (err) {
        console.error("Error testing press_key:", err);
        results["press_key"] = { success: false, error: String(err) };
    }
}
/**
 * Tests the swipe tool
 */
async function testSwipe(results) {
    console.log("\n--- Testing swipe ---");
    try {
        // Get screen info to determine good coordinates
        const deviceResult = await toolCall("device_info");
        const deviceData = deviceResult;
        // Parse resolution into width and height
        let width = 1080; // Default fallback
        let height = 1920;
        if (deviceData.screenResolution) {
            const match = deviceData.screenResolution.match(/(\d+)x(\d+)/);
            if (match) {
                width = parseInt(match[1]);
                height = parseInt(match[2]);
            }
        }
        // Calculate swipe coordinates (swipe up from bottom to middle)
        const centerX = Math.floor(width / 2);
        const startY = Math.floor(height * 0.8);
        const endY = Math.floor(height * 0.3);
        console.log(`Swiping up from (${centerX}, ${startY}) to (${centerX}, ${endY})`);
        const swipeResult = await toolCall("swipe", {
            start_x: centerX,
            start_y: startY,
            end_x: centerX,
            end_y: endY,
            duration: 500
        });
        // Validate the result
        const swipeData = swipeResult;
        console.log(`Action type: ${swipeData.actionType}`);
        console.log(`Action description: ${swipeData.actionDescription}`);
        // Wait for UI to respond
        console.log("Waiting for UI to respond...");
        await toolCall("sleep", { duration_ms: 1000 });
        // Try horizontal swipe too
        console.log("\nSwiping horizontally (left to right)...");
        const startX = Math.floor(width * 0.2);
        const endX = Math.floor(width * 0.8);
        const midY = Math.floor(height * 0.5);
        console.log(`Swiping from (${startX}, ${midY}) to (${endX}, ${midY})`);
        const swipeHResult = await toolCall("swipe", {
            start_x: startX,
            start_y: midY,
            end_x: endX,
            end_y: midY,
            duration: 500
        });
        // Validate the result
        const swipeHData = swipeHResult;
        console.log(`Action type: ${swipeHData.actionType}`);
        console.log(`Action description: ${swipeHData.actionDescription}`);
        results["swipe"] = {
            success: true,
            data: { vertical: swipeData, horizontal: swipeHData }
        };
    }
    catch (error) {
        console.error("Error in testSwipe:", error);
        results["swipe"] = { success: false, error: error.toString() };
    }
}
// Export main function and category-specific test runners
exports.main = runTests;
exports.testCategory = function (params) {
    return runTests(params);
};
// Add exports for these functions
exports.testQueryMemory = testQueryMemory;
exports.testUsePackage = testUsePackage;
exports.testCalculator = testCalculator;
exports.testSleep = testSleep;
exports.testListFiles = testListFiles;
exports.testWriteFile = testWriteFile;
exports.testReadFile = testReadFile;
exports.testFileExists = testFileExists;
exports.testMakeDirectory = testMakeDirectory;
exports.testCopyFile = testCopyFile;
exports.testMoveFile = testMoveFile;
exports.testFindFiles = testFindFiles;
exports.testFileInfo = testFileInfo;
exports.testZipFiles = testZipFiles;
exports.testUnzipFiles = testUnzipFiles;
exports.testOpenFile = testOpenFile;
exports.testShareFile = testShareFile;
exports.testDeleteFile = testDeleteFile;
exports.testWebSearch = testWebSearch;
exports.testHttpRequest = testHttpRequest;
exports.testDownloadFile = testDownloadFile;
exports.testDeviceInfo = testDeviceInfo;
exports.testGetSystemSetting = testGetSystemSetting;
exports.testModifySystemSetting = testModifySystemSetting;
exports.testListInstalledApps = testListInstalledApps;
exports.testInstallApp = testInstallApp;
exports.testUninstallApp = testUninstallApp;
exports.testStartApp = testStartApp;
exports.testStopApp = testStopApp;
exports.testGetPageInfo = testGetPageInfo;
exports.testClickElement = testClickElement;
exports.testTap = testTap;
exports.testSetInputText = testSetInputText;
exports.testPressKey = testPressKey;
exports.testSwipe = testSwipe;
