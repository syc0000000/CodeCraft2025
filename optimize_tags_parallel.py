#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys
import subprocess
import re
import time
import signal
import traceback
import argparse
import concurrent.futures
from datetime import datetime

# ANSI颜色代码
RED = '\033[91m'
GREEN = '\033[92m'
BLUE = '\033[94m'
YELLOW = '\033[93m'
CYAN = '\033[96m'
RESET = '\033[0m'

def parse_args():
    parser = argparse.ArgumentParser(description='使用线程池并行测试一个period的最优标签数量')
    parser.add_argument('-period', type=int, required=True, help='要测试的period，从0开始')
    parser.add_argument('-min_tags', type=int, default=5, help='最小标签数量')
    parser.add_argument('-max_tags', type=int, default=16, help='最大标签数量')
    parser.add_argument('-workers', type=int, default=3, help='并行工作线程数量')
    return parser.parse_args()

def compile_java():
    """编译Java程序"""
    print(f"{CYAN}正在编译Java程序...{RESET}")
    try:
        subprocess.run(['javac', '-d', 'build', './Main.java'], check=True)
        print(f"{GREEN}Java编译成功{RESET}")
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
        # 先读取现有内容
        lines = []
        if os.path.exists(file_path):
            with open(file_path, 'r') as f:
                lines = f.readlines()
        
        # 过滤出注释行和不需要修改的行
        header_lines = [line for line in lines if line.startswith('#')]
        param_lines = [line for line in lines if not line.startswith('#') and line.strip()]
        
        # 将参数行解析为字典
        params_dict = {}
        for line in param_lines:
            line = line.strip()
            if ':' in line:
                period, count = line.split(':')
                params_dict[int(period.strip())] = int(count.strip())
        
        # 更新或添加新的参数
        for period, count in optimal_params.items():
            params_dict[period] = count
        
        # 写入文件
        with open(file_path, 'w') as f:
            # 写入注释头
            if not header_lines:
                f.write("# Period : TagCount\n")
                f.write("# 自动生成的最优参数文件，请勿手动修改\n")
                f.write(f"# 更新时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
            else:
                for line in header_lines:
                    f.write(line)
                f.write("\n")
            
            # 按period排序写入参数
            for period in sorted(params_dict.keys()):
                f.write(f"{period}: {params_dict[period]}\n")
        return True
    except Exception as e:
        print(f"{RED}保存参数文件失败: {e}{RESET}")
        return False

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

def run_test_with_params(period, tag_count, end_tick=None, timeout=1800):
    """运行测试，传递命令行参数并返回分数"""
    print(f"{CYAN}[标签数={tag_count}] 开始测试 Period {period}...{RESET}")
    
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
    
    # 准备命令，传递period和tags参数给Java程序
    cmd = [
        'python3', './test/run.py', 
        interactor, input_file, 
        f'java -cp ./build -Dperiod={period} -Dtags={tag_count} Main', 
        '-d', '-r', str(end_tick)
    ]
    
    result_file = f"./test/result_p{period}_t{tag_count}.txt"
    
    # 清理之前的结果
    if os.path.exists(result_file):
        os.remove(result_file)
    
    # 运行命令
    start_time = time.time()
    score = None
    process = None
    
    try:
        # 先清理可能残留的进程，确保环境干净
        cleanup_processes()
        
        process = subprocess.Popen(
            cmd, 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE, 
            text=True, 
            bufsize=1
        )
        
        import threading
        import queue
        
        # 用于存储score的队列
        score_queue = queue.Queue()
        stderr_output = []
        stdout_output = []
        
        def read_stderr(proc, queue):
            try:
                for line in proc.stderr:
                    stderr_output.append(line)
                    # 检查是否有当前period的分数信息
                    score_pattern = r'\[INFO\] Period (\d+) \((\d+)s~(\d+)s\) has been completed, used ([\d.]+)s, current score: ([\d.]+)\.'
                    score_match = re.search(score_pattern, line)
                    
                    if score_match:
                        period_num, start, end, used_time, score_val = score_match.groups()
                        period_num = int(period_num)
                        
                        if period_num == period + 1:  # 注意: period从0开始，但输出从1开始
                            try:
                                queue.put(float(score_val))
                                # 找到了目标period的分数，终止进程
                                if proc.poll() is None:
                                    proc.terminate()
                            except Exception as e:
                                print(f"{RED}读取分数时出错: {e}{RESET}")
            except Exception as e:
                print(f"{RED}读取stderr线程出错: {e}{RESET}")
        
        def read_stdout(proc):
            try:
                for line in proc.stdout:
                    stdout_output.append(line)
            except Exception as e:
                print(f"{RED}读取stdout线程出错: {e}{RESET}")
        
        # 启动线程读取stderr
        stderr_thread = threading.Thread(target=read_stderr, args=(process, score_queue))
        stderr_thread.daemon = True
        stderr_thread.start()
        
        # 启动线程读取stdout
        stdout_thread = threading.Thread(target=read_stdout, args=(process,))
        stdout_thread.daemon = True
        stdout_thread.start()
        
        # 设置超时
        elapsed = 0
        while elapsed < timeout:
            # 检查是否有分数
            try:
                score = score_queue.get_nowait()
                break
            except queue.Empty:
                pass
            
            # 检查进程是否已结束
            if process.poll() is not None:
                break
            
            # 睡眠一小段时间
            time.sleep(1)
            elapsed += 1
            
            # 每隔30秒打印一次心跳，确认脚本仍在运行
            if elapsed % 30 == 0:
                print(f"{BLUE}[标签数={tag_count}] 仍在运行中，已经过{elapsed}秒...{RESET}")
        
        # 如果超时，终止进程
        if process and process.poll() is None:
            print(f"{YELLOW}[标签数={tag_count}] 进程超时，强制终止{RESET}")
            try:
                process.terminate()
                time.sleep(1)
                if process and process.poll() is None:
                    process.kill()
                    time.sleep(1)
            except Exception as e:
                print(f"{RED}终止进程时出错: {e}{RESET}")
        
        # 等待线程结束（最多5秒）
        try:
            if stderr_thread.is_alive():
                stderr_thread.join(5)
            if stdout_thread.is_alive():
                stdout_thread.join(5)
        except Exception as e:
            print(f"{RED}等待线程结束时出错: {e}{RESET}")
        
        # 确保进程已结束
        try:
            if process and process.poll() is None:
                process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            print(f"{RED}进程等待超时{RESET}")
            if process and process.poll() is None:
                process.kill()
        except Exception as e:
            print(f"{RED}等待进程结束时出错: {e}{RESET}")
        
        # 如果没有从进程输出获取到分数，尝试从结果文件读取
        if score is None:
            # 保存stderr和stdout输出到文件
            try:
                with open(result_file, 'w') as f:
                    f.write("===== STDOUT =====\n")
                    for line in stdout_output:
                        f.write(line)
                    f.write("\n===== STDERR =====\n")
                    for line in stderr_output:
                        f.write(line)
            except Exception as e:
                print(f"{RED}保存输出到文件时出错: {e}{RESET}")
            
            # 从测试目录的result.txt文件中读取分数
            try:
                result_txt_path = "./test/result.txt"
                if os.path.exists(result_txt_path):
                    with open(result_txt_path, 'r') as f:
                        content = f.read()
                        period_score_pattern = rf"Period {period+1} \(.*?\) score: ([\d.]+)"
                        file_match = re.search(period_score_pattern, content)
                        if file_match:
                            score = float(file_match.group(1))
                            print(f"{GREEN}从结果文件中读取到分数: {score}{RESET}")
            except Exception as e:
                print(f"{RED}[标签数={tag_count}] 读取结果文件失败: {e}{RESET}")
    
    except Exception as e:
        print(f"{RED}[标签数={tag_count}] 运行测试时发生错误: {e}{RESET}")
        traceback.print_exc()
    
    finally:
        # 确保进程被终止
        try:
            if process and process.poll() is None:
                process.kill()
        except:
            pass
        
        # 清理残留进程
        try:
            cleanup_processes()
        except:
            pass
    
    elapsed_time = time.time() - start_time
    if score is not None:
        print(f"{GREEN}[标签数={tag_count}] Period {period+1} 测试完成，分数: {score:.4f}，耗时: {elapsed_time:.1f}秒{RESET}")
    else:
        print(f"{RED}[标签数={tag_count}] Period {period+1} 测试失败，耗时: {elapsed_time:.1f}秒{RESET}")
    
    return period, tag_count, score

def parallel_optimize_period(period, min_tags=5, max_tags=16, max_workers=3):
    """使用线程池并行测试不同标签数量"""
    # 加载已有参数
    optimal_params = load_optimal_params()
    
    # 如果当前period已经有最优值，直接返回
    if period in optimal_params:
        print(f"{YELLOW}Period {period} 已有最优值: {optimal_params[period]}，跳过测试{RESET}")
        return optimal_params[period]
    
    # 编译Java程序
    if not compile_java():
        return None
    
    # 清理可能的残留进程
    cleanup_processes()
    
    # 创建任务列表
    tasks = []
    for tag_count in range(min_tags, max_tags + 1):
        tasks.append((period, tag_count))
    
    # 实际使用的工作线程数不超过任务数
    max_workers = min(max_workers, len(tasks))
    print(f"{BLUE}使用 {max_workers} 个工作线程测试 {len(tasks)} 个标签配置{RESET}")
    
    # 使用线程池并行执行测试
    results = []
    
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
            # 提交任务
            future_to_task = {}
            for period, tag_count in tasks:
                future = executor.submit(run_test_with_params, period, tag_count)
                future_to_task[future] = (period, tag_count)
            
            # 获取结果
            for future in concurrent.futures.as_completed(future_to_task):
                try:
                    period, tag_count, score = future.result()
                    if score is not None:
                        results.append((tag_count, score))
                        # 每获得一个结果就立即保存，以防中途崩溃丢失数据
                        with open(f"period_{period}_results.txt", "a") as f:
                            f.write(f"{tag_count}: {score}\n")
                except Exception as e:
                    period, tag_count = future_to_task[future]
                    print(f"{RED}获取 Period {period} 标签数 {tag_count} 的结果时发生错误: {e}{RESET}")
    except Exception as e:
        print(f"{RED}线程池执行时发生错误: {e}{RESET}")
        traceback.print_exc()
    
    # 查找最优参数
    if results:
        # 按分数排序
        results.sort(key=lambda x: x[1], reverse=True)
        best_tag_count, best_score = results[0]
        
        print(f"\n{CYAN}===== Period {period} 测试结果 ====={RESET}")
        for tag_count, score in sorted(results, key=lambda x: x[0]):
            if (tag_count, score) == (best_tag_count, best_score):
                print(f"{GREEN}标签数量 {tag_count}: 分数 {score:.4f} (最佳){RESET}")
            else:
                print(f"标签数量 {tag_count}: 分数 {score:.4f}")
        
        # 保存最优参数
        try:
            optimal_params[period] = best_tag_count
            save_success = save_optimal_params({period: best_tag_count})
            if save_success:
                print(f"{GREEN}已保存最优参数到配置文件{RESET}")
            else:
                print(f"{YELLOW}保存最优参数到配置文件失败，但优化过程已完成{RESET}")
        except Exception as e:
            print(f"{RED}保存最优参数时出错: {e}{RESET}")
            # 写入应急文件
            try:
                with open(f"period_{period}_best_tag.txt", "w") as f:
                    f.write(f"{best_tag_count}\n")
                print(f"{YELLOW}已将最优参数写入应急文件 period_{period}_best_tag.txt{RESET}")
            except:
                pass
        
        print(f"\n{GREEN}Period {period} 的最优标签数量: {best_tag_count}，分数: {best_score:.4f}{RESET}")
        return best_tag_count
    else:
        print(f"{RED}未能为Period {period}找到有效结果{RESET}")
        return None

def main():
    # 处理Ctrl+C信号
    def signal_handler(sig, frame):
        print(f"\n{RED}接收到中断信号，正在清理并退出...{RESET}")
        cleanup_processes()
        sys.exit(0)
    
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # 解析命令行参数
    args = parse_args()
    
    # 确保logs目录存在
    if not os.path.exists('logs'):
        os.makedirs('logs')
    
    print(f"{CYAN}===== 开始优化 Period {args.period} ====={RESET}")
    print(f"测试标签数量范围: {args.min_tags}-{args.max_tags}")
    print(f"并行工作线程数: {args.workers}\n")
    
    # 运行并行优化
    try:
        best_tag_count = parallel_optimize_period(
            args.period, 
            args.min_tags, 
            args.max_tags, 
            args.workers
        )
        
        if best_tag_count is not None:
            print(f"\n{GREEN}优化完成! Period {args.period} 的最优标签数量: {best_tag_count}{RESET}")
        else:
            print(f"\n{RED}Period {args.period} 的优化失败{RESET}")
            return 1
    except Exception as e:
        print(f"{RED}优化过程中发生错误: {e}{RESET}")
        traceback.print_exc()
        return 1
    finally:
        # 确保清理所有进程
        cleanup_processes()
    
    return 0

if __name__ == "__main__":
    exit_code = main()
    sys.exit(exit_code) 
