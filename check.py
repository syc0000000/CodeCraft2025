#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys
import subprocess
import re
import shutil
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
    parser = argparse.ArgumentParser(description='运行CodeCraft评测并处理输出')
    parser.add_argument('-release', action='store_true', help='发布模式，不使用调试选项')
    parser.add_argument('-big', action='store_true', help='使用更大的输入文件')
    return parser.parse_args()

def main():
    args = parse_args()
    
    # 编译Java程序
    try:
        subprocess.run(['javac', '-d', 'build', './Main.java'], check=True)
    except subprocess.CalledProcessError:
        print(f"{RED}Java编译失败{RESET}")
        return 1
    
    # 清理日志
    if os.path.exists('logs/app.log'):
        os.remove('logs/app.log')
    
    # 设置命令行参数
    debug_option = "" if args.release else "-d"
    input_file = "./test/sample_official.in" if args.big else "./test/sample_official.in"
    
    # 根据操作系统选择正确的interactor
    if sys.platform == "win32":
        interactor = "./test/interactor.exe"
    elif sys.platform == "darwin":
        interactor = "./test/interactor_mac"
    else:
        interactor = "./test/interactor"
    
    # 备份上次结果（如果存在）
    result_file = "./test/result.txt"
    past_file = "./test/past.txt"
    
    past_scores = {i: 0 for i in range(1, 50)}
    if os.path.exists(result_file):
        try:
            shutil.copy2(result_file, past_file)
            with open(past_file, 'r') as f:
                for line in f:
                    match = re.search(r'Period (\d+) \((\d+)s~(\d+)s\) score: ([\d.]+)', line)
                    if match:
                        period = int(match.group(1))
                        score = float(match.group(4))
                        past_scores[period] = score
        except Exception as e:
            print(f"{RED}备份上次结果时出错: {e}{RESET}")
    
    # 准备命令
    cmd = [
        'python3', './test/run.py', 
        interactor, input_file, 
        'java -cp ./build Main', 
        debug_option, '-r', '75000'
    ]
    cmd = [c for c in cmd if c]  # 移除空字符串
    
    # 打开文件准备写入结果
    with open(result_file, 'w') as result_output:
        # 运行命令并处理输出
        process = subprocess.Popen(
            cmd, 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE, 
            text=True, 
            bufsize=1
        )
        
        for line in process.stderr:
            # 处理程序日志 - 带日期时间的日志
            log_pattern = r'^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) \[(\d+)\] \[(\w+)\] \[(\w+)\] (.+)'
            log_match = re.match(log_pattern, line)
            
            # 处理分数日志
            score_pattern = r'\[INFO\] Period (\d+) \((\d+)s~(\d+)s\) has been completed, used ([\d.]+)s, current score: ([\d.]+)'
            score_match = re.search(score_pattern, line)
            
            if log_match:
                timestamp, thread, level, tag, content = log_match.groups()
                # 根据日志级别设置颜色
                if level == "INFO":
                    level_color = BLUE
                elif level == "WARN":
                    level_color = YELLOW
                elif level == "ERROR":
                    level_color = RED
                else:
                    level_color = RESET
                
                # 输出彩色日志
                print(f"{CYAN}{timestamp}{RESET} [{thread}] [{level_color}{level}{RESET}] [{GREEN}{tag}{RESET}] {content}")
            
            elif score_match:
                period, start, end, used_time, score = score_match.groups()
                period = int(period)
                
                # 去除可能存在的结尾标点符号
                score = score.rstrip('.,')
                score = float(score)
                
                # 保存结果到文件
                result_output.write(f"Period {period} ({start}s~{end}s) score: {score}\n")
                result_output.flush()
                
                # 比较与上次运行的差异
                diff_str = ""
                diff = score - past_scores[period]
                if diff > 0:
                    diff_str = f" ({RED}+{diff:.4f}{RESET})"
                elif diff < 0:
                    diff_str = f" ({GREEN}{diff:.4f}{RESET})"
                else:
                    diff_str = f" ({RESET}{diff:.4f}{RESET})"
                
                # 只输出时间片和分数，进行对齐
                print(f"Period {period:2d} - ({start:5s}s~{end:5s}s) - {score:11.4f}{diff_str}")
            else:
                # 其他日志原样输出
                print(line, end='')
        
        # 等待进程结束
        process.wait()
    
    return process.returncode

if __name__ == "__main__":
    sys.exit(main()) 