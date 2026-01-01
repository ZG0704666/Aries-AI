# JavaScript 工具调用

JavaScript 工具调用是一种功能强大的脚本机制，允许在 Android 应用中使用 JavaScript 编写自定义工具并与现有工具系统集成。本实现利用 WebView 执行 JavaScript 代码，并提供与原生代码的交互能力。

## 特性概览

- **完整的 JavaScript 支持**：使用标准 ES6+ JavaScript 语法
- **第三方 JS 库**：内置了常用的工具库，如 Lodash 核心功能
- **工具调用集成**：通过 `toolCall` 函数与现有工具系统无缝对接
- **参数传递**：支持通过 `params` 对象访问传入的参数
- **结果返回**：使用 `complete()` 函数返回执行结果
- **错误处理**：支持 JavaScript 标准的 try-catch 错误处理
- **异步操作**：支持 Promise 和异步/等待模式

## 使用指南

### 基本语法

JavaScript 工具使用标准的 JavaScript 语法，支持 ES6+ 特性：

```javascript
// 使用参数
const userId = params.userId;

// 计算和逻辑
const result = Math.sqrt(16) + Math.pow(2, 3);

// 使用函数和库
const isEmpty = _.isEmpty([]);

// 返回结果
complete({
    status: "success",
    data: result
});
```

### 参数使用

参数通过 `params` 对象传入，可以直接访问：

```javascript
// 访问参数
const query = params.query || "默认查询";
const limit = parseInt(params.limit) || 10;

// 使用参数
console.log(`查询: ${query}, 限制: ${limit}`);
```

### 工具调用

使用 `toolCall` 函数调用其他工具：

```javascript
// 调用格式: toolCall(工具类型, 工具名称, 参数对象)
const result = toolCall("default", "calculate", {
    expression: "sqrt(16) + pow(2, 3)"
});

// 使用调用结果
console.log("计算结果:", result);
```

工具调用可以嵌套或在条件语句中使用：

```javascript
// 条件调用
if (toolCall("default", "file_exists", { path: "/sdcard/my_file.txt" })) {
    const fileContent = toolCall("default", "read_file", { path: "/sdcard/my_file.txt" });
    // 处理文件内容
}
```

### 返回结果

使用 `complete()` 函数返回结果：

```javascript
// 返回简单值
complete("操作成功");

// 返回复杂对象
complete({
    status: "success",
    data: {
        id: 123,
        name: "测试数据",
        items: [1, 2, 3]
    },
    timestamp: new Date().toISOString()
});
```

如果未明确调用 `complete()`，脚本会自动返回一个默认结果。

### 错误处理

使用标准的 JavaScript 错误处理：

```javascript
try {
    // 可能出错的代码
    const data = JSON.parse(invalidJson);
    complete(data);
} catch (e) {
    // 处理错误
    console.log("发生错误:", e.message);
    complete({
        status: "error",
        message: e.message
    });
}
```

## 内置库说明

### Lodash 核心功能

提供常用的实用函数：

```javascript
// 类型检查
_.isString("test");  // true
_.isNumber(42);      // true
_.isArray([1,2,3]);  // true
_.isObject({a:1});   // true

// 集合操作
_.isEmpty([]);       // true
_.forEach([1,2,3], item => console.log(item));
_.map({a:1, b:2}, (v, k) => `${k}=${v}`);  // ["a=1", "b=2"]
```

### 数据工具库

提供数据处理辅助函数：

```javascript
// JSON处理
const obj = dataUtils.parseJson('{"name":"test"}');
const json = dataUtils.stringifyJson({name:"test"});

// 日期格式化
const formattedDate = dataUtils.formatDate(new Date());
```

## 示例

### 简单计算

```javascript
const a = 5;
const b = 7;
const result = a * b + Math.sqrt(a*a + b*b);
complete(result);  // 返回 43.6023
```

### 参数使用与工具调用

```javascript
const query = params.query || "默认查询";
const searchResult = toolCall("default", "search", {
    query: query,
    maxResults: 10
});
complete({
    query: query,
    results: searchResult
});
```

### 数据处理

```javascript
// 示例JSON数据
const data = {
    users: [
        { id: 1, name: "张三", age: 28 },
        { id: 2, name: "李四", age: 32 },
        { id: 3, name: "王五", age: 45 }
    ]
};

// 数据处理
const userNames = data.users.map(user => user.name);
const averageAge = data.users.reduce((sum, user) => sum + user.age, 0) / data.users.length;

complete({
    names: userNames,
    averageAge: averageAge
});
```

## 与 OperScript 的区别

与原有的 OperScript 相比，JavaScript 工具调用具有以下优势：

1. **标准语法**：使用广泛采用的 JavaScript 语法，无需学习自定义语言
2. **丰富的内置功能**：利用 JavaScript 内置的丰富功能和标准库
3. **生态系统**：可以集成大量现有的 JavaScript 库
4. **开发工具支持**：享受成熟的 JavaScript IDE 和开发工具支持
5. **更灵活的错误处理**：使用标准的 try-catch 错误处理机制
6. **更强大的数据处理**：支持现代 JavaScript 数组和对象操作方法
7. **异步支持**：支持 Promise、async/await 等现代异步编程模式

## 在代码中使用 JavaScript 工具

```kotlin
// 获取 JavaScript 工具管理器实例
val jsToolManager = JsToolManager.getInstance(context, packageManager)

// 创建要执行的工具
val tool = AITool(
    name = "CustomJsTool",
    parameters = listOf(
        ToolParameter(name = "param1", value = "value1"),
        ToolParameter(name = "param2", value = "value2")
    )
)

// 执行脚本
val script = """
    const result = "处理参数: " + params.param1 + ", " + params.param2;
    complete(result);
"""
val result = jsToolManager.executeScript(script, tool)

// 处理结果
if (result.success) {
    println("脚本执行结果: ${result.result}")
} else {
    println("脚本执行失败: ${result.error}")
}
``` 