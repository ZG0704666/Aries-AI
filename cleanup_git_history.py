#!/usr/bin/env python3
"""
Git历史清理脚本 - 移除硬编码密钥
用于清除 jmcomic 示例文件中的硬编码密钥及任何被误提交的敏感文件

使用前请备份仓库，操作后将强制推送到远端
"""

import subprocess
import sys

# 要移除的敏感值
SENSITIVE_VALUES = [
    '18comicAPP',           # APP_TOKEN_SECRET
    '18comicAPPContent',    # APP_TOKEN_SECRET_2  
    '185Hcomic3PAPP7R',     # APP_DATA_SECRET
]

# 要清除的文件模式（路径中包含这些则清除整个提交记录）
PATTERNS_TO_REMOVE = [
    'local.properties',
    '.env',
    '*.jks',
    '*.keystore',
    '*.p12',
    '*.pfx'
]

def run_cmd(cmd, check=True):
    """执行命令"""
    print(f"[执行] {cmd}")
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if check and result.returncode != 0:
        print(f"[错误] {result.stderr}")
        sys.exit(1)
    return result.returncode == 0

def clean_git_history():
    """清除Git历史中的敏感数据"""
    print("\n=" * 60)
    print("Git历史敏感数据清理 - 手动模式")
    print("=" * 60)
    
    print("\n【强烈建议】")
    print("1. 请先创建备份分支:")
    print("   git branch backup-before-clean")
    print("\n2. 使用 git-filter-repo (推荐，比BFG更安全):")
    print("   # 安装: pip install git-filter-repo")
    print("   # 清除包含特定字符串的提交:")
    for val in SENSITIVE_VALUES:
        print(f"   git filter-repo --invert-regex --path jmcomic")
    
    print("\n3. 或使用 git filter-branch (传统方法):")
    print("   # 清除所有历史中特定文件:")
    print("   git filter-branch --tree-filter 'rm -f examples/jmcomic.ts examples/jmcomic.js' HEAD")
    
    print("\n4. 强制推送到远端 (危险操作):")
    print("   git push origin --force --all")
    print("   git push origin --force --tags")
    
    print("\n【操作步骤】")
    print("1. 备份当前仓库")
    print("2. 创建备份分支")  
    print("3. 选择上述工具之一进行清理")
    print("4. 验证文件已清除")
    print("5. 强制推送")
    print("6. 通知所有开发者重新克隆仓库")

def main():
    clean_git_history()
    
    print("\n【关键密钥需要立即旋转】")
    print("请通知相关团队旋转以下密钥:")
    for val in SENSITIVE_VALUES:
        print(f"  - {val} (来自 jmcomic 示例)")
    print("  - 所有 GITHUB_CLIENT_SECRET 值")
    print("  - 所有 Android 签名密钥口令")

if __name__ == '__main__':
    main()
