def optimize_sequence(sequence):
    # r操作的成本列表
    r_costs = [64, 52, 42, 34, 28, 23, 19, 16]
    
    # 获取序列长度
    n = len(sequence)
    
    # 创建动态规划表 dp[i][j] 表示到序列的第i个位置，有j个连续r操作的最小成本
    # j从0开始，0表示最后一个操作是p，j>0表示有j个连续的r
    dp = [[float('inf')] * (n + 1) for _ in range(n)]
    
    # 记录选择，用于重建最优解
    choice = [[-1] * (n + 1) for _ in range(n)]
    
    # 初始化第一个操作
    if sequence[0] == 'r':
        dp[0][1] = r_costs[0]  # 第一个r的成本是64
    else:  # 'p'
        dp[0][0] = 1  # 保持为p
        dp[0][1] = r_costs[0]  # 变为r
        choice[0][0] = 0  # 保持为p
        choice[0][1] = 1  # 变为r
    
    # 填充动态规划表
    for i in range(1, n):
        if sequence[i] == 'r':
            # 必须是r，考虑前一个操作
            for j in range(1, i + 2):
                # r之后跟r，延续连续r
                if j > 1 and dp[i-1][j-1] != float('inf'):
                    r_cost = r_costs[min(j-1, len(r_costs)-1)]
                    if dp[i-1][j-1] + r_cost < dp[i][j]:
                        dp[i][j] = dp[i-1][j-1] + r_cost
                        choice[i][j] = j-1
                # p之后跟r，重新开始连续r
                if dp[i-1][0] != float('inf'):
                    if dp[i-1][0] + r_costs[0] < dp[i][1]:
                        dp[i][1] = dp[i-1][0] + r_costs[0]
                        choice[i][1] = 0
        else:  # 'p'
            # 可以保持为p
            for j in range(i + 1):
                if dp[i-1][j] != float('inf') and dp[i-1][j] + 1 < dp[i][0]:
                    dp[i][0] = dp[i-1][j] + 1
                    choice[i][0] = j
            
            # 可以变为r
            for j in range(1, i + 2):
                # r之后跟r，延续连续r
                if j > 1 and dp[i-1][j-1] != float('inf'):
                    r_cost = r_costs[min(j-1, len(r_costs)-1)]
                    if dp[i-1][j-1] + r_cost < dp[i][j]:
                        dp[i][j] = dp[i-1][j-1] + r_cost
                        choice[i][j] = j-1
                # p之后跟r，重新开始连续r
                if dp[i-1][0] != float('inf'):
                    if dp[i-1][0] + r_costs[0] < dp[i][1]:
                        dp[i][1] = dp[i-1][0] + r_costs[0]
                        choice[i][1] = 0
    
    # 找出最小成本和对应的结束状态
    min_cost = float('inf')
    end_state = -1
    for j in range(n + 1):
        if dp[n-1][j] < min_cost:
            min_cost = dp[n-1][j]
            end_state = j
    
    # 重建最优解
    result = [''] * n
    i = n - 1
    j = end_state
    
    while i >= 0:
        if j == 0:  # 当前是p
            result[i] = 'p'
            j = choice[i][j]
        else:  # 当前是r
            result[i] = 'r'
            j = choice[i][j]
        i -= 1
    
    return result, min_cost

# 验证最优解
def calculate_cost(seq):
    cost = 0
    consecutive_r = 0
    r_costs = [64, 52, 42, 34, 28, 23, 19, 16]
    
    for op in seq:
        if op == 'r':
            consecutive_r += 1
            r_cost_index = min(consecutive_r - 1, len(r_costs) - 1)
            cost += r_costs[r_cost_index]
        else:  # 'p'
            cost += 1
            consecutive_r = 0
    
    return cost

# 性能测试
import time
import random
import matplotlib.pyplot as plt

def generate_best_case(size):
    """生成最好情况的测试序列 - 全部为r"""
    return ['r'] * size

def generate_worst_case(size):
    """生成最差情况的测试序列 - r和p交替出现"""
    return ['r', 'p'] * (size // 2) + (['r'] if size % 2 != 0 else [])

def generate_random_case(size):
    """生成随机测试序列"""
    return [random.choice(['r', 'p']) for _ in range(size)]

def run_performance_test(sizes=None, repetitions=3):
    """运行性能测试"""
    if sizes is None:
        sizes = [10, 50, 100, 200, 500, 1000, 2000]
    
    results = {
        "最好情况": [],
        "最差情况": [],
        "随机情况": []
    }
    
    for size in sizes:
        # 最好情况
        best_times = []
        for _ in range(repetitions):
            seq = generate_best_case(size)
            start_time = time.time()
            optimize_sequence(seq)
            end_time = time.time()
            best_times.append(end_time - start_time)
        results["最好情况"].append((size, sum(best_times) / repetitions))
        
        # 最差情况
        worst_times = []
        for _ in range(repetitions):
            seq = generate_worst_case(size)
            start_time = time.time()
            optimize_sequence(seq)
            end_time = time.time()
            worst_times.append(end_time - start_time)
        results["最差情况"].append((size, sum(worst_times) / repetitions))
        
        # 随机情况
        random_times = []
        for _ in range(repetitions):
            seq = generate_random_case(size)
            start_time = time.time()
            optimize_sequence(seq)
            end_time = time.time()
            random_times.append(end_time - start_time)
        results["随机情况"].append((size, sum(random_times) / repetitions))
        
        print(f"完成规模 {size} 的测试")
    
    return results, sizes

def plot_results(results, sizes):
    """绘制性能测试结果"""
    plt.figure(figsize=(10, 6))
    
    # 翻译结果键为英文
    labels_map = {
        "最好情况": "Best Case",
        "最差情况": "Worst Case",
        "随机情况": "Random Case"
    }
    
    for case, times in results.items():
        x = sizes
        y = [t for _, t in times]
        plt.plot(x, y, marker='o', label=labels_map.get(case, case))
    
    plt.xlabel('Sequence Length')
    plt.ylabel('Execution Time (seconds)')
    plt.title('Operation Sequence Optimization Algorithm Performance Test')
    plt.legend()
    plt.grid(True)
    
    # 添加复杂度参考线
    max_time = max([t for case_times in results.values() for _, t in case_times])
    max_size = max(sizes)
    
    # O(n²)参考线
    n_squared = [max_time * (size/max_size)**2 for size in sizes]
    plt.plot(sizes, n_squared, 'k--', alpha=0.3, label='O(n²)')
    
    plt.savefig('performance_test.png')
    plt.show()

def analyze_performance(results):
    """分析性能结果"""
    print("\n性能分析:")
    
    # 对于每个情况，计算大致的时间复杂度
    for case, times in results.items():
        sizes = [size for size, _ in times]
        exec_times = [t for _, t in times]
        
        if len(sizes) >= 2:
            # 计算最后两个点的比值作为一个粗略的复杂度估计
            last_ratio = exec_times[-1] / exec_times[-2]
            size_ratio = sizes[-1] / sizes[-2]
            complexity = last_ratio / size_ratio
            
            complexity_class = "线性"
            if complexity > 1.8:
                complexity_class = "平方级"
            elif complexity > 1.4:
                complexity_class = "超线性"
            
            print(f"{case}的时间复杂度似乎是{complexity_class}的 (比值约为 {complexity:.2f})")
        
        print(f"{case}的平均执行时间：{sum(exec_times)/len(exec_times):.6f}秒")
    
    # 计算最好情况和最差情况的差距
    best_times = [t for _, t in results["最好情况"]]
    worst_times = [t for _, t in results["最差情况"]]
    
    avg_best = sum(best_times) / len(best_times)
    avg_worst = sum(worst_times) / len(worst_times)
    
    print(f"\n最好情况与最差情况的平均时间差距: {avg_worst/avg_best:.2f}倍")

# 测试用例
def run_example():
    sequence = ['r', 'r', 'p','r','r','r','r']
    optimal_sequence, min_cost = optimize_sequence(sequence)
    
    print(f"原始序列: {sequence}")
    print(f"最优序列: {optimal_sequence}")
    print(f"最小成本: {min_cost}")
    
    # 验证原始序列成本
    original_cost = calculate_cost(sequence)
    print(f"原始序列成本: {original_cost}")
    
    # 验证最优序列成本
    calculated_min_cost = calculate_cost(optimal_sequence)
    print(f"计算得到的最优序列成本: {calculated_min_cost}")

if __name__ == "__main__":
    import sys
    
    # 默认参数
    run_test = True
    custom_sizes = None
    repetitions = 3
    
    # 命令行参数处理
    if len(sys.argv) > 1:
        if sys.argv[1] == "example":
            run_test = False
            run_example()
        elif sys.argv[1] == "test":
            # 自定义测试规模
            if len(sys.argv) > 2:
                custom_sizes = list(map(int, sys.argv[2].split(',')))
            # 自定义重复次数
            if len(sys.argv) > 3:
                repetitions = int(sys.argv[3])
    
    if run_test:
        print("开始性能测试...")
        print(f"测试规模: {custom_sizes if custom_sizes else '默认'}")
        print(f"每个规模重复次数: {repetitions}")
        
        results, sizes = run_performance_test(custom_sizes, repetitions)
        analyze_performance(results)
        
        try:
            plot_results(results, sizes)
            print("性能测试图表已保存为 'performance_test.png'")
        except Exception as e:
            print(f"无法绘制图表: {e}")
            print("这可能是因为matplotlib未安装或在没有显示器的环境中运行")
        
        # 输出CSV格式的结果
        print("\n性能测试结果 (CSV格式):")
        print("规模,最好情况,最差情况,随机情况")
        for i, size in enumerate(sizes):
            best_time = results["最好情况"][i][1]
            worst_time = results["最差情况"][i][1]
            random_time = results["随机情况"][i][1]
            print(f"{size},{best_time:.6f},{worst_time:.6f},{random_time:.6f}")
