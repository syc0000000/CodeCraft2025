import os
import sys
import subprocess
import re
import json
import argparse
import time
import signal
import traceback
from datetime import datetime

# ANSI颜色代码
RED = '\033[91m'
GREEN = '\033[92m'
BLUE = '\033[94m'
YELLOW = '\033[93m'
CYAN = '\033[96m'
RESET = '\033[0m'

def compile_java():
    """编译Java程序"""
    print(f"{CYAN}正在编译Java程序...{RESET}")
    try:
        subprocess.run(['javac', '-d', 'build', './Main.java'], check=True)
        return True
    except subprocess.CalledProcessError:
        print(f"{RED}Java编译失败{RESET}")
        return False
    
    # 清理测试相关的所有进程
def cleanup_processes():
    """清理所有与测试相关的进程（java、interactor和测试脚本）"""
    print(f"{YELLOW}正在清理测试相关进程...{RESET}")
    
    try:
        # 在Linux/WSL环境中使用pkill命令清理进程
        if sys.platform != "win32":
            # 清理java进程
            subprocess.run(["pkill", "-9", "-f", "java -cp ./build Main"], 
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
            # 清理interactor进程
            subprocess.run(["pkill", "-9", "-f", "interactor"], 
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
            # 清理python run.py进程
            subprocess.run(["pkill", "-9", "-f", "python3 ./test/run.py"], 
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        else:
            # Windows环境，使用taskkill
            subprocess.run(["taskkill", "/F", "/FI", "IMAGENAME eq java.exe", "/FI", "WINDOWTITLE eq *build Main*"],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
            subprocess.run(["taskkill", "/F", "/FI", "IMAGENAME eq interactor.exe"],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
            # 清理python run.py进程
            subprocess.run(["taskkill", "/F", "/FI", "IMAGENAME eq python.exe", "/FI", "WINDOWTITLE eq *run.py*"],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        
        # 等待一小段时间确保进程已终止
        time.sleep(1)
        
        print(f"{GREEN}清理进程完成{RESET}")
    except Exception as e:
        print(f"{RED}清理进程时出错: {e}{RESET}")

