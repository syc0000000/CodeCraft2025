#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys
import subprocess
import time
import traceback
import signal
import argparse
from datetime import datetime

# ANSI颜色代码
RED = '\033[91m'
GREEN = '\033[92m'
BLUE = '\033[94m'
YELLOW = '\033[93m'
CYAN = '\033[96m'
RESET = '\033[0m'

def parse_args():
    parser = argparse.ArgumentParser(description='依次运行所有period的优化')
    parser.add_argument('-start', type=int, default=0, help='起始period，从0开始')
    parser.add_argument('-end', type=int, default=47, help='结束period，默认到47（共48个period）')
    parser.add_argument('-min_tags', type=int, default=3, help='最小标签数量')
    parser.add_argument('-max_tags', type=int, default=16, help='最大标签数量')
    return parser.parse_args()

# 防止子进程被终止时主进程也终止
def handle_sigterm(signum, frame):
    print(f"{RED}收到SIGTERM信号，但我们会继续运行{RESET}")

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
            # 清理optimize_tags.py进程 (当前进程除外)
            my_pid = os.getpid()
            subprocess.run(f"ps -ef | grep optimize_tags.py | grep -v {my_pid} | awk '{{print $2}}' | xargs -r kill -9",
                          shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
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

def main():
    # 注册信号处理器
    signal.signal(signal.SIGTERM, handle_sigterm)
    signal.signal(signal.SIGINT, handle_sigterm)
    
    args = parse_args()
    
    # 先清理可能存在的遗留进程
    cleanup_processes()
    
    total_periods = args.end - args.start + 1
    current = 0
    
    # 确保脚本可执行
    if not os.access('optimize_tags.py', os.X_OK):
        os.chmod('optimize_tags.py', 0o755)
    
    # 记录运行日志
    log_file = f"period_optimization_log_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
    
    with open(log_file, 'w') as log:
        log.write(f"开始优化Period {args.start} 到 {args.end}\n")
        log.write(f"最小标签数: {args.min_tags}, 最大标签数: {args.max_tags}\n")
        log.write(f"开始时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        log.write("-" * 50 + "\n")
    
    # 依次运行每个period的优化
    for period in range(args.start, args.end + 1):
        current += 1
        print(f"\n{CYAN}===== 开始优化 Period {period} [{current}/{total_periods}] ====={RESET}")
        
        # 确保没有残留进程
        cleanup_processes()
        
        # 记录开始时间
        start_time = time.time()
        
        # 运行优化脚本
        cmd = [
            'python3', './optimize_tags.py', 
            '-period', str(period),
            '-min_tags', str(args.min_tags),
            '-max_tags', str(args.max_tags)
        ]
        
        try:
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            
            # 读取输出并实时显示
            for line in process.stdout:
                print(line.strip())
                with open(log_file, 'a') as log:
                    log.write(f"[P{period}] {line}")
            
            # 读取错误输出
            error_output = process.stderr.read()
            if error_output:
                print(f"{RED}错误输出:{RESET}\n{error_output}")
                with open(log_file, 'a') as log:
                    log.write(f"[P{period}] ERROR: {error_output}\n")
            
            # 等待进程完成
            return_code = process.wait()
            
            if return_code != 0:
                print(f"{RED}Period {period} 优化失败，返回码: {return_code}{RESET}")
                with open(log_file, 'a') as log:
                    log.write(f"[P{period}] 优化失败，返回码: {return_code}\n")
            
            # 计算用时
            elapsed_time = time.time() - start_time
            elapsed_minutes = int(elapsed_time // 60)
            elapsed_seconds = int(elapsed_time % 60)
            
            # 再次确保没有残留进程
            cleanup_processes()
            
            print(f"{GREEN}===== Period {period} 优化完成, 用时 {elapsed_minutes}分{elapsed_seconds}秒 ====={RESET}")
            with open(log_file, 'a') as log:
                log.write(f"[P{period}] 优化完成, 用时 {elapsed_minutes}分{elapsed_seconds}秒\n")
                log.write("-" * 50 + "\n")
        
        except Exception as e:
            print(f"{RED}运行Period {period}优化时发生错误: {e}{RESET}")
            print(f"{RED}错误详情: {traceback.format_exc()}{RESET}")
            with open(log_file, 'a') as log:
                log.write(f"[P{period}] 运行出错: {e}\n")
                log.write(f"[P{period}] 错误详情: {traceback.format_exc()}\n")
                log.write("-" * 50 + "\n")
    
    print(f"\n{GREEN}所有period优化完成！{RESET}")
    with open(log_file, 'a') as log:
        log.write(f"\n优化全部完成，结束时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"{RED}主程序发生致命错误: {e}{RESET}")
        print(f"{RED}错误详情: {traceback.format_exc()}{RESET}")
        # 在异常退出前也清理进程
        try:
            cleanup_processes()
        except:
            pass
        # 记录错误到文件
        with open(f"fatal_error_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log", 'w') as f:
            f.write(f"致命错误: {e}\n")
            f.write(traceback.format_exc()) 