# API 文档: `cryptojs.d.ts`

本文档详细介绍了 `cryptojs.d.ts` 文件中定义的 API。它提供了 [CryptoJS](https://cryptojs.gitbook.io/docs/) 库的一个子集，通过原生桥接实现，用于在脚本中执行常见的加密操作。

## 概述

所有加密功能都通过一个全局的 `CryptoJS` 对象提供。这个实现是针对特定需求定制的，可能与标准的 CryptoJS 库在行为上略有不同。

-   **`CryptoJS.MD5`**: 计算 MD5 哈希值。
-   **`CryptoJS.AES.decrypt`**: 使用 AES 算法解密数据。
-   **`CryptoJS.enc`**: 处理编码格式转换。

---

## `CryptoJS` 对象详解

### `CryptoJS.MD5(message: string): WordArray`

计算输入字符串的 MD5 哈希值。

-   **`message`**: 需要计算哈希的字符串。
-   **返回值**: 返回一个 `WordArray` 对象，你可以调用其 `.toString()` 方法来获取十六进制格式的哈希字符串。

**示例:**

```typescript
const message = "Hello, World!";
const hash = CryptoJS.MD5(message);
const hashString = hash.toString(); // e.g., "65a8e27d8879283831b664bd8b7f0ad4"
console.log(`MD5 Hash: ${hashString}`);
```

### `CryptoJS.AES.decrypt(ciphertext: string, key: any, cfg?: any): WordArray`

使用 AES 算法进行解密。此函数的具体行为（尤其是 `key` 和 `cfg` 参数的用法）与原生实现紧密相关。

-   **`ciphertext`**: 需要解密的密文字符串。
-   **`key`**: 解密密钥。
-   **`cfg`**: 配置对象，可能包含模式（mode）、填充（padding）等信息。
-   **返回值**: 返回一个 `WordArray` 对象，代表解密后的明文。你需要调用 `.toString(CryptoJS.enc.Utf8)` 来获取可读的 UTF-8 字符串。

**示例 (特定于项目中的用法):**

```typescript
// 假设这是从某个 API 获取的加密数据
const encryptedData = "U2FsdGVkX1...encrypted..."; 
const timestamp = "1678886400"; // 密钥
const secret = "my-secret-phrase"; // 另一个配置参数

// 解密
const decrypted = CryptoJS.AES.decrypt(encryptedData, timestamp, { iv: secret });

// 将解密后的 WordArray 转换为 Utf8 字符串
const plaintext = decrypted.toString(CryptoJS.enc.Utf8);
console.log(`Decrypted Text: ${plaintext}`);
```

### `CryptoJS.enc`

用于处理不同编码格式的对象。

-   **`CryptoJS.enc.Hex.parse(hexStr: string): WordArray`**: 将一个十六进制字符串解析成 `WordArray` 对象。
-   **`CryptoJS.enc.Utf8`**: 一个标记，用于在 `.toString()` 方法中指定输出编码为 UTF-8。

### `WordArray` 接口

这是加密操作（如 `MD5` 和 `AES.decrypt`）返回的结果对象的类型。

-   **`toString(encoding?: any): string`**: 是其最重要的方法，用于将内部的字节数组转换为字符串。
    -   如果不提供 `encoding`，通常默认输出为十六进制字符串。
    -   如果提供 `CryptoJS.enc.Utf8`，则输出为 UTF-8 编码的字符串。 