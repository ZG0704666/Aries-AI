# API 文档: `jimp.d.ts`

本文档详细介绍了 `jimp.d.ts` 文件中定义的 API。它提供了 [Jimp](https://github.com/oliver-moran/jimp) 图像处理库的一个子集，通过原生桥接实现，允许在脚本中执行基本的图像操作。

## 概述

所有图像处理功能都通过一个全局的 `Jimp` 对象提供。你可以用它来读取、创建、修改和导出图片。核心概念是 `JimpWrapper` 对象，它代表一个被加载到内存中的图像实例。

-   **`Jimp.read`**: 从 Base64 字符串中读取图像。
-   **`Jimp.create`**: 创建一个指定尺寸的空白图像。
-   **`JimpWrapper`**: 图像实例，提供裁剪、合成、获取尺寸和导出等方法。

---

## `Jimp` 对象详解

### `Jimp.read(base64: string): Promise<JimpWrapper>`

从一个 Base64 编码的字符串中异步读取图像数据，并返回一个 `JimpWrapper` 实例。

-   **`base64`**: 包含图像数据的 Base64 字符串 (不含 data URI 前缀，如 `data:image/png;base64,`)。
-   **返回值**: 一个 `Promise`，成功时解析为一个可供操作的 `JimpWrapper` 对象。

### `Jimp.create(w: number, h: number): Promise<JimpWrapper>`

创建一个指定宽度和高度的空白（通常是黑色）图像。

-   **`w`**: 图像的宽度（像素）。
-   **`h`**: 图像的高度（像素）。
-   **返回值**: 一个 `Promise`，成功时解析为一个新的 `JimpWrapper` 对象。

### `Jimp` 常量

-   `Jimp.MIME_JPEG`: 字符串常量 `'image/jpeg'`。
-   `Jimp.MIME_PNG`: 字符串常量 `'image/png'`。

---

## `JimpWrapper` 类详解

这是一个图像的包装器实例，在你通过 `Jimp.read` 或 `Jimp.create` 获得它之后，就可以调用以下方法进行操作。

### 方法

-   `crop(x: number, y: number, w: number, h: number): Promise<JimpWrapper>`:
    裁剪图像。返回一个新的、代表裁剪后区域的 `JimpWrapper` 实例。
    -   `(x, y)`: 裁剪区域左上角的坐标。
    -   `(w, h)`: 裁剪区域的宽度和高度。

-   `composite(src: JimpWrapper, x: number, y: number): Promise<this>`:
    将另一张图片 (`src`) 合成（粘贴）到当前图片上。
    -   `src`: 要合成的源 `JimpWrapper` 图像。
    -   `(x, y)`: 在当前图片上开始粘贴的左上角坐标。
    -   **返回值**: 返回 `this`，允许链式调用。

-   `getWidth(): Promise<number>`:
    获取图像的宽度。

-   `getHeight(): Promise<number>`:
    获取图像的高度。

-   `getBase64(mime: string): Promise<string>`:
    将当前图像编码为 Base64 字符串。
    -   `mime`: 图像的 MIME 类型，应使用 `Jimp.MIME_JPEG` 或 `Jimp.MIME_PNG`。

-   `release(): Promise<void>`:
    **（重要）** 释放与此图像实例关联的底层原生资源。当你完成对一个 `JimpWrapper` 对象的所有操作后，应调用此方法以避免内存泄漏。

**示例: 裁剪并保存图片**
```typescript
async function cropImage() {
    // 假设 base64ImageString 是一个 PNG 图片的 Base64 字符串
    const base64ImageString = "...";

    let image = null;
    let croppedImage = null;
    try {
        image = await Jimp.read(base64ImageString);
        
        // 从 (10, 20) 坐标开始，裁剪一个 100x150 的区域
        croppedImage = await image.crop(10, 20, 100, 150);
        
        // 获取裁剪后图片的 Base64
        const croppedBase64 = await croppedImage.getBase64(Jimp.MIME_PNG);
        
        // 可以将 croppedBase64 写入文件
        await Tools.Files.writeBinary("/sdcard/Pictures/cropped_image.png", croppedBase64);

        complete({ success: true, message: "图片裁剪成功！" });

    } catch (error) {
        complete({ success: false, message: `图片处理失败: ${error.message}` });
    } finally {
        // 确保释放所有 Jimp 对象的内存
        if (image) await image.release();
        if (croppedImage) await croppedImage.release();
    }
}
``` 