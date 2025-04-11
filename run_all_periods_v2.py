#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys
import subprocess
import time
import signal
import traceback
import argparse
import shutil
from datetime import datetime

# ANSI颜色代码
RED = '\033[91m'
GREEN = '\033[92m'
BLUE = '\033[94m'
YELLOW = '\033[93m'
CYAN = '\033[96m'
RESET = '\033[0m'

def parse_args():
    parser = argparse.ArgumentParser(description='依次运行所有period的并行优化')
    parser.add_argument('-start', type=int, default=0, help='起始period，从0开始')
    parser.add_argument('-end', type=int, default=47, help='结束period，默认到47（共48个period）')
    parser.add_argument('-min_tags', type=int, default=5, help='最小标签数量')
    parser.add_argument('-max_tags', type=int, default=16, help='最大标签数量')
    parser.add_argument('-workers', type=int, default=6, help='每个period的并行工作线程数量')
    parser.add_argument('-resume', action='store_true', help='从上次中断的地方继续')
    return parser.parse_args()

def cleanup_processes():
    """清理所有与测试相关的进程"""
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
            # 清理optimize_tags_parallel.py进程
            subprocess.run(["pkill", "-9", "-f", "python3 ./optimize_tags_parallel.py"],
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
        
        time.sleep(1)
        print(f"{GREEN}清理进程完成{RESET}")
    except Exception as e:
        print(f"{RED}清理进程时出错: {e}{RESET}")

def load_optimal_params(file_path="optimal_period_tags.txt"):
    """加载已有的最优参数"""
    optimal_params = {}
    if os.path.exists(file_path):
        try:
            with open(file_path, 'r') as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith('#'):
                        try:
                            period, count = line.split(':')
                            optimal_params[int(period.strip())] = int(count.strip())
                        except (ValueError, IndexError):
                            continue
        except Exception as e:
            print(f"{RED}读取参数文件失败: {e}{RESET}")
    return optimal_params

def backup_optimal_params():
    """备份优化参数文件"""
    src = "optimal_period_tags.txt"
    if os.path.exists(src):
        dst = f"optimal_period_tags_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
        try:
            shutil.copy2(src, dst)
            print(f"{GREEN}已将参数文件备份为 {dst}{RESET}")
            return True
        except Exception as e:
            print(f"{RED}备份参数文件失败: {e}{RESET}")
    return False

def optimize_single_period(period, min_tags, max_tags, workers):
    """使用并行优化脚本优化单个period"""
    print(f"\n{CYAN}===== 开始优化 Period {period} ====={RESET}")
    
    # 先清理进程
    cleanup_processes()
    
    # 记录开始时间
    start_time = time.time()
    
    # 运行优化脚本
    cmd = [
        'python3', './optimize_tags_parallel.py', 
        '-period', str(period),
        '-min_tags', str(min_tags),
        '-max_tags', str(max_tags),
        '-workers', str(workers)
    ]
    
    # 检查是否已经有这个period的最优参数
    optimal_params = load_optimal_params()
    if period in optimal_params:
        print(f"{YELLOW}Period {period} 已有最优值: {optimal_params[period]}，考虑跳过{RESET}")
        response = input(f"是否仍要优化Period {period}? (y/n): ").strip().lower()
        if response != 'y':
            print(f"{BLUE}跳过Period {period}的优化{RESET}")
            return True
    
    try:
        # 运行优化脚本
        process = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False
        )
        
        # 输出脚本的输出
        if process.stdout:
            print(process.stdout)
        
        # 输出脚本的错误
        if process.stderr:
            print(f"{RED}错误输出:{RESET}\n{process.stderr}")
        
        # 检查返回码
        if process.returncode != 0:
            print(f"{RED}Period {period} 优化失败，返回码: {process.returncode}{RESET}")
            
            # 检查是否生成了应急文件
            emergency_file = f"period_{period}_best_tag.txt"
            if os.path.exists(emergency_file):
                try:
                    with open(emergency_file, 'r') as f:
                        best_tag = int(f.read().strip())
                        print(f"{YELLOW}从应急文件中读取Period {period}的最佳标签数量: {best_tag}{RESET}")
                        
                        # 更新optimal_period_tags.txt
                        params = load_optimal_params()
                        params[period] = best_tag
                        
                        with open("optimal_period_tags.txt", 'w') as f:
                            f.write("# Period : TagCount\n")
                            f.write("# 自动生成的最优参数文件，请勿手动修改\n")
                            f.write(f"# 更新时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
                            for p in sorted(params.keys()):
                                f.write(f"{p}: {params[p]}\n")
                        
                        print(f"{GREEN}已将应急文件中的参数更新到配置文件{RESET}")
                        return True
                except Exception as e:
                    print(f"{RED}读取应急文件失败: {e}{RESET}")
            
            # 如果没有应急文件或读取失败，则返回失败
            return False
        
        # 计算用时
        elapsed_time = time.time() - start_time
        elapsed_minutes = int(elapsed_time // 60)
        elapsed_seconds = int(elapsed_time % 60)
        
        print(f"{GREEN}===== Period {period} 优化完成, 用时 {elapsed_minutes}分{elapsed_seconds}秒 ====={RESET}")
        return True
    
    except Exception as e:
        print(f"{RED}运行Period {period}优化时发生错误: {e}{RESET}")
        print(f"{RED}错误详情: {traceback.format_exc()}{RESET}")
        return False
    
    finally:
        # 确保清理所有进程
        cleanup_processes()

def find_last_completed_period():
    """查找最后一个已完成优化的period"""
    optimal_params = load_optimal_params()
    if not optimal_params:
        return -1
    
    # 找到最大的period
    return max(optimal_params.keys(), default=-1)

def main():
    # 注册信号处理器
    def signal_handler(sig, frame):
        print(f"\n{RED}接收到中断信号，正在清理并退出...{RESET}")
        cleanup_processes()
        sys.exit(0)
    
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # 解析命令行参数
    args = parse_args()
    
    # 先清理可能存在的遗留进程
    cleanup_processes()
    
    # 确保优化脚本存在并可执行
    if not os.path.exists('optimize_tags_parallel.py'):
        print(f"{RED}错误: optimize_tags_parallel.py 脚本不存在{RESET}")
        return 1
    
    if not os.access('optimize_tags_parallel.py', os.X_OK):
        os.chmod('optimize_tags_parallel.py', 0o755)
    
    # 备份当前的参数文件
    backup_optimal_params()
    
    # 如果指定了resume，则从最后一个完成的period开始
    if args.resume:
        last_period = find_last_completed_period()
        if last_period >= 0:
            args.start = last_period + 1
            print(f"{YELLOW}从Period {args.start}继续优化 (上一个完成的Period是{last_period}){RESET}")
    
    # 记录运行日志
    log_file = f"period_optimization_log_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
    
    with open(log_file, 'w') as log:
        log.write(f"开始优化Period {args.start} 到 {args.end}\n")
        log.write(f"最小标签数: {args.min_tags}, 最大标签数: {args.max_tags}\n")
        log.write(f"每个period的并行工作线程数: {args.workers}\n")
        log.write(f"开始时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        log.write("-" * 50 + "\n")
    
    # 统计
    total_periods = args.end - args.start + 1
    successful_periods = 0
    failed_periods = 0
    
    # 依次运行每个period的优化
    for period in range(args.start, args.end + 1):
        current = period - args.start + 1
        print(f"\n{CYAN}===== 开始优化 Period {period} [{current}/{total_periods}] ====={RESET}")
        
        # 确保没有残留进程
        cleanup_processes()
        
        # 记录开始时间
        period_start_time = time.time()
        
        # 优化当前period
        success = optimize_single_period(period, args.min_tags, args.max_tags, args.workers)
        
        # 计算用时
        elapsed_time = time.time() - period_start_time
        elapsed_minutes = int(elapsed_time // 60)
        elapsed_seconds = int(elapsed_time % 60)
        
        # 更新统计并记录日志
        with open(log_file, 'a') as log:
            if success:
                successful_periods += 1
                log.write(f"Period {period} 优化成功, 用时 {elapsed_minutes}分{elapsed_seconds}秒\n")
            else:
                failed_periods += 1
                log.write(f"Period {period} 优化失败, 用时 {elapsed_minutes}分{elapsed_seconds}秒\n")
            
            log.write("-" * 50 + "\n")
        
        # 如果失败，询问是否继续
        if not success:
            retry = input(f"{YELLOW}Period {period} 优化失败，是否重试? (y/n): {RESET}").strip().lower()
            if retry == 'y':
                print(f"{BLUE}重新尝试Period {period}的优化{RESET}")
                period -= 1  # 回退，下一轮会重新处理这个period
                continue
            
            continue_next = input(f"{YELLOW}是否继续优化下一个Period? (y/n): {RESET}").strip().lower()
            if continue_next != 'y':
                print(f"{RED}用户选择停止优化{RESET}")
                break
    
    # 输出总结
    print(f"\n{CYAN}===== 优化任务完成 ====={RESET}")
    print(f"总共尝试优化 {total_periods} 个period")
    print(f"成功: {successful_periods}")
    print(f"失败: {failed_periods}")
    
    with open(log_file, 'a') as log:
        log.write(f"\n优化全部完成，结束时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        log.write(f"总共尝试优化 {total_periods} 个period\n")
        log.write(f"成功: {successful_periods}\n")
        log.write(f"失败: {failed_periods}\n")
    
    # 最终清理
    cleanup_processes()
    
    return 0 if failed_periods == 0 else 1

if __name__ == "__main__":
    try:
        exit_code = main()
        sys.exit(exit_code)
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
