#!/bin/bash

# 清理build目录
rm -rf build
mkdir -p build

# 编译Java文件到build目录
javac -d build $(find . -name "*.java")

echo "编译完成，.class文件已输出到build目录" 