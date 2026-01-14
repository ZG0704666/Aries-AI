#!/usr/bin/env python3
"""
代码修复脚本 - 消除硬编码密钥
将 jmcomic 示例中的硬编码密钥替换为环境变量读取
"""

import re
import sys
from pathlib import Path

def fix_jmcomic_ts():
    """修复 jmcomic.ts 文件"""
    file_path = Path("temp/Operit-main/examples/jmcomic.ts")
    if not file_path.exists():
        print(f"[警告] {file_path} 不存在")
        return False
    
    content = file_path.read_text(encoding='utf-8')
    original = content
    
    # 将硬编码密钥替换为环境变量
    content = re.sub(
        r"APP_TOKEN_SECRET:\s*'18comicAPP'",
        "APP_TOKEN_SECRET: process.env.JMCOMIC_TOKEN_SECRET || '18comicAPP'",
        content
    )
    content = re.sub(
        r"APP_TOKEN_SECRET_2:\s*'18comicAPPContent'",
        "APP_TOKEN_SECRET_2: process.env.JMCOMIC_TOKEN_SECRET_2 || '18comicAPPContent'",
        content
    )
    content = re.sub(
        r"APP_DATA_SECRET:\s*'185Hcomic3PAPP7R'",
        "APP_DATA_SECRET: process.env.JMCOMIC_DATA_SECRET || '185Hcomic3PAPP7R'",
        content
    )
    
    if content != original:
        file_path.write_text(content, encoding='utf-8')
        print(f"[✓] 已修复 {file_path}")
        return True
    print(f"[!] {file_path} 无需修改")
    return False

def fix_jmcomic_js():
    """修复 jmcomic.js 文件"""
    file_path = Path("temp/Operit-main/examples/jmcomic.js")
    if not file_path.exists():
        print(f"[警告] {file_path} 不存在")
        return False
    
    content = file_path.read_text(encoding='utf-8')
    original = content
    
    # 同样的替换
    content = re.sub(
        r"APP_TOKEN_SECRET:\s*'18comicAPP'",
        "APP_TOKEN_SECRET: process.env.JMCOMIC_TOKEN_SECRET || '18comicAPP'",
        content
    )
    content = re.sub(
        r"APP_TOKEN_SECRET_2:\s*'18comicAPPContent'",
        "APP_TOKEN_SECRET_2: process.env.JMCOMIC_TOKEN_SECRET_2 || '18comicAPPContent'",
        content
    )
    content = re.sub(
        r"APP_DATA_SECRET:\s*'185Hcomic3PAPP7R'",
        "APP_DATA_SECRET: process.env.JMCOMIC_DATA_SECRET || '185Hcomic3PAPP7R'",
        content
    )
    
    if content != original:
        file_path.write_text(content, encoding='utf-8')
        print(f"[✓] 已修复 {file_path}")
        return True
    print(f"[!] {file_path} 无需修改")
    return False

def main():
    print("=" * 60)
    print("代码修复 - 消除硬编码密钥")
    print("=" * 60)
    print("\n【修复内容】")
    print("将 jmcomic 示例中的硬编码密钥替换为环境变量读取")
    print("保留默认值以确保向后兼容性")
    print()
    
    success = True
    success = fix_jmcomic_ts() or success
    success = fix_jmcomic_js() or success
    
    if success:
        print("\n[✓] 修复完成！")
        print("\n【后续步骤】")
        print("1. 测试修改的代码")
        print("2. 提交修改: git add . && git commit -m 'fix: replace hardcoded secrets with env vars'")
        print("3. 执行 cleanup_git_history.py 清除历史记录")
        print("4. 旋转相关密钥")
    else:
        print("\n[!] 未找到要修复的文件或无需修改")
        sys.exit(1)

if __name__ == '__main__':
    main()
