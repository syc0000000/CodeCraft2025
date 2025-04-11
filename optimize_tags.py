#!/usr/bin/env python3
# -*- coding: utf-8 -*-

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

# 全局变量，用于跟踪子进程
current_process = None

def parse_args():
    parser = argparse.ArgumentParser(description='自动化测试CodeCraft最优参数')
    parser.add_argument('-period', type=int, default=0, help='要测试的period，从0开始')
    parser.add_argument('-min_tags', type=int, default=3, help='最小标签数量')
    parser.add_argument('-max_tags', type=int, default=16, help='最大标签数量')
    return parser.parse_args()

# 处理终止信号
def handle_signal(signum, frame):
    print(f"{RED}收到信号 {signum}，正在优雅退出...{RESET}")
    # 终止当前正在运行的进程
    global current_process
    if current_process is not None and current_process.poll() is None:
        try:
            print(f"{YELLOW}正在终止子进程...{RESET}")
            current_process.terminate()
            # 等待最多5秒让进程结束
            for _ in range(50):
                if current_process.poll() is not None:
                    break
                time.sleep(0.1)
            # 如果进程还在运行，强制杀死
            if current_process.poll() is None:
                print(f"{RED}子进程未响应终止信号，强制杀死...{RESET}")
                current_process.kill()
        except Exception as e:
            print(f"{RED}终止子进程时出错: {e}{RESET}")
    # 如果是严重的信号，退出程序
    if signum in [signal.SIGTERM, signal.SIGINT]:
        print(f"{RED}正在退出主程序...{RESET}")
        sys.exit(1)

def compile_java():
    """编译Java程序"""
    print(f"{CYAN}正在编译Java程序...{RESET}")
    try:
        subprocess.run(['javac', '-d', 'build', './Main.java'], check=True)
        return True
    except subprocess.CalledProcessError:
        print(f"{RED}Java编译失败{RESET}")
        return False

def load_optimal_params(file_path="optimal_period_tags.txt"):
    """加载已有的最优参数"""
    optimal_params = {}
    if os.path.exists(file_path):
        with open(file_path, 'r') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#'):
                    try:
                        period, count = line.split(':')
                        optimal_params[int(period.strip())] = int(count.strip())
                    except (ValueError, IndexError):
                        continue
    return optimal_params

def save_optimal_params(optimal_params, file_path="optimal_period_tags.txt"):
    """保存最优参数到文件"""
    # 先创建备份
    if os.path.exists(file_path):
        backup_path = f"{file_path}.bak"
        try:
            import shutil
            shutil.copy2(file_path, backup_path)
        except Exception as e:
            print(f"{YELLOW}创建参数文件备份失败: {e}{RESET}")
    
    # 保存新的参数文件
    try:
        with open(file_path, 'w') as f:
            f.write("# Period : TagCount\n")
            f.write("# 自动生成的最优参数文件，请勿手动修改\n")
            f.write(f"# 更新时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
            for period in sorted(optimal_params.keys()):
                f.write(f"{period}: {optimal_params[period]}\n")
        return True
    except Exception as e:
        print(f"{RED}保存参数文件失败: {e}{RESET}")
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

def run_test(tag_count, period, end_tick=None):
    """运行一次测试，并返回指定period的分数"""
    global current_process
    
    # 在每次测试前清理之前的进程
    cleanup_processes()
    
    # 根据操作系统选择正确的interactor
    if sys.platform == "win32":
        interactor = "./test/interactor.exe"
    elif sys.platform == "darwin":
        interactor = "./test/interactor_mac"
    else:
        interactor = "./test/interactor"
    
    input_file = "./test/sample_practice.in"
    
    # 计算要运行到的tick
    if end_tick is None:
        # 每个period是1800个tick，我们运行到当前period结束
        end_tick = (period + 1) * 1800
    
    # 准备命令
    cmd = [
        'python3', './test/run.py', 
        interactor, input_file, 
        'java -cp ./build Main', 
        '-d', '-r', str(end_tick)
    ]
    
    result_file = "./test/result.txt"
    
    # 清理之前的结果
    if os.path.exists(result_file):
        os.remove(result_file)
    
    # 运行命令
    print(f"{CYAN}正在运行测试，标签数量={tag_count}，运行到period={period}...{RESET}")
    
    # 使用timeout方式运行，防止程序卡住
    try:
        current_process = subprocess.Popen(
            cmd, 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE, 
            text=True, 
            bufsize=1
        )
        
        current_period_score = None
        
        # 从stderr读取输出并处理，同时设置超时
        import threading
        import queue
        
        def enqueue_output(out, q):
            for line in iter(out.readline, ''):
                q.put(line)
            out.close()
        
        # 创建队列和线程处理stderr输出
        q = queue.Queue()
        t = threading.Thread(target=enqueue_output, args=(current_process.stderr, q))
        t.daemon = True  # 设置为守护线程，主线程结束时会自动退出
        t.start()
        
        # 设置超时时间（秒）
        timeout = 1800  # 30分钟超时，足够运行完一个period
        start_time = time.time()
        
        # 循环读取队列中的输出
        while time.time() - start_time < timeout:
            try:
                line = q.get_nowait()
                # 正确的正则表达式，保留末尾的点号
                score_pattern = r'\[INFO\] Period (\d+) \((\d+)s~(\d+)s\) has been completed, used ([\d.]+)s, current score: ([\d.]+)\.'
                score_match = re.search(score_pattern, line)
                
                if score_match:
                    period_num, start, end, used_time, score = score_match.groups()
                    period_num = int(period_num)
                    
                    print(f"检测到Period {period_num} 分数行: {line.strip()}")
                    
                    if period_num == period + 1:  # 注意: period从0开始，但输出从1开始
                        current_period_score = float(score)
                        print(f"{GREEN}Period {period_num} 的分数: {score}{RESET}")
                        # 找到目标period的分数后可以终止测试
                        if current_process.poll() is None:
                            print(f"{YELLOW}找到目标period分数，终止测试进程...{RESET}")
                            current_process.terminate()
                            time.sleep(0.5)
                            if current_process.poll() is None:
                                print(f"{RED}进程未响应终止信号，强制杀死...{RESET}")
                                current_process.kill()
                        break
            except queue.Empty:
                # 队列为空，休眠一小段时间
                time.sleep(0.1)
                # 检查进程是否已结束
                if current_process.poll() is not None:
                    print(f"{YELLOW}进程已结束，返回码: {current_process.returncode}{RESET}")
                    break
        
        # 确保进程已结束
        if current_process.poll() is None:
            print(f"{YELLOW}进程超时，强制终止...{RESET}")
            current_process.terminate()
            time.sleep(0.5)
            if current_process.poll() is None:
                current_process.kill()
        
        if current_process.poll() is None:
            print(f"{RED}无法终止进程，这不太可能发生{RESET}")
        else:
            current_process.wait()
        
        if current_period_score is None:
            print(f"{RED}未能获取到Period {period+1}的分数{RESET}")
            # 检查结果文件是否存在并且包含分数信息
            if os.path.exists(result_file):
                try:
                    with open(result_file, 'r') as f:
                        content = f.read()
                        period_score_pattern = rf"Period {period+1} \(.*?\) score: ([\d.]+)"
                        file_match = re.search(period_score_pattern, content)
                        if file_match:
                            current_period_score = float(file_match.group(1))
                            print(f"{GREEN}从结果文件中获取到Period {period+1}的分数: {current_period_score}{RESET}")
                except Exception as e:
                    print(f"{RED}尝试从结果文件获取分数时出错: {e}{RESET}")
        
        # 重置全局进程引用
        current_process = None
        
        # 测试结束后再次清理进程
        cleanup_processes()
        
        return current_period_score
    
    except Exception as e:
        print(f"{RED}运行测试时发生错误: {e}{RESET}")
        print(f"{RED}错误详情: {traceback.format_exc()}{RESET}")
        # 确保子进程被终止
        try:
            if current_process is not None and current_process.poll() is None:
                current_process.kill()
        except:
            pass
        # 重置全局进程引用
        current_process = None
        return None

def optimize_period(period, min_tags=3, max_tags=16):
    """为指定period找出最优的标签数量"""
    # 加载已有参数
    optimal_params = load_optimal_params()
    
    # 如果当前period已经有最优值，直接返回
    if period in optimal_params:
        print(f"{YELLOW}Period {period} 已有最优值: {optimal_params[period]}{RESET}")
        return optimal_params
    
    # 编译Java程序
    if not compile_java():
        return optimal_params
    
    best_score = 0
    best_tag_count = min_tags
    
    # 测试不同的标签数量
    for tag_count in range(min_tags, max_tags + 1):
        # 更新参数文件
        test_params = optimal_params.copy()
        test_params[period] = tag_count
        if not save_optimal_params(test_params):
            print(f"{RED}无法保存测试参数，跳过标签数量 {tag_count}{RESET}")
            continue
        
        # 运行测试
        score = run_test(tag_count, period)
        
        if score is None:
            print(f"{RED}无法获取Period {period} 的分数，跳过标签数量 {tag_count}{RESET}")
            continue
        
        # 更新最佳分数
        if score > best_score:
            best_score = score
            best_tag_count = tag_count
            print(f"{GREEN}发现新的最佳标签数量: {tag_count}，分数: {score}{RESET}")
    
    # 保存最优参数
    optimal_params[period] = best_tag_count
    save_optimal_params(optimal_params)
    
    print(f"{CYAN}Period {period} 的最优标签数量: {best_tag_count}，分数: {best_score}{RESET}")
    return optimal_params

def main():
    # 注册信号处理器
    signal.signal(signal.SIGTERM, handle_signal)
    signal.signal(signal.SIGINT, handle_signal)
    
    args = parse_args()
    
    # 先清理可能存在的遗留进程
    cleanup_processes()
    
    # 确保logs目录存在
    if not os.path.exists('logs'):
        os.makedirs('logs')
    
    try:
        # 优化指定period
        optimize_period(args.period, args.min_tags, args.max_tags)
    except Exception as e:
        print(f"{RED}主程序发生错误: {e}{RESET}")
        print(f"{RED}错误详情: {traceback.format_exc()}{RESET}")
        # 确保子进程被终止
        global current_process
        if current_process is not None and current_process.poll() is None:
            try:
                current_process.terminate()
                time.sleep(0.5)
                if current_process.poll() is None:
                    current_process.kill()
            except:
                pass
        return 1
    
    return 0

if __name__ == "__main__":
    exit_code = 1
    try:
        exit_code = main()
    except Exception as e:
        print(f"{RED}致命错误: {e}{RESET}")
        print(f"{RED}错误详情: {traceback.format_exc()}{RESET}")
    sys.exit(exit_code) 