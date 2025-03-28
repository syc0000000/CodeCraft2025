import numpy as np
import random
import matplotlib.pyplot as plt
from typing import List, Dict, Tuple, Set
import time
import concurrent.futures
import multiprocessing

# 问题参数
TAGS = [1968, 799, 2121, 1849, 944, 502, 1933, 1507, 494, 1540, 631, 1715, 434, 1107, 545, 2196]
NUM_DISKS = 10
MAX_TAGS_PER_DISK = 4
MIN_TAGS_PER_DISK = 1
MAX_SPLITS_PER_TAG = 6

# 遗传算法参数 - 增加代数和种群大小
POPULATION_SIZE = 200
NUM_GENERATIONS = 2000
MUTATION_RATE_START = 0.3
MUTATION_RATE_END = 0.05
CROSSOVER_RATE = 0.8
ELITE_SIZE = 20
TOURNAMENT_SIZE = 7
CONVERGENCE_THRESHOLD = 50  # 如果最佳适应度连续这么多代没有改善，则提前停止
THREADS = max(1, multiprocessing.cpu_count() - 1)  # 使用CPU数量减1的线程数

# 假设的多个时间点的读取量矩阵 (时间点 x 标签)
# 实际使用时可以从文件读取或通过参数传入
READS_PER_TIMEPOINT = np.array([
    [10, 5, 20, 15, 8, 6, 18, 12, 5, 14, 7, 15, 4, 10, 6, 22],  # 时间点1
    [15, 8, 25, 10, 12, 4, 20, 14, 6, 18, 5, 12, 3, 8, 7, 24],  # 时间点2
    [8, 6, 18, 12, 10, 8, 15, 10, 4, 16, 9, 10, 5, 12, 4, 20],  # 时间点3
    [12, 10, 22, 18, 6, 5, 25, 16, 7, 10, 8, 20, 6, 15, 5, 18]   # 时间点4
])

# 表示个体的类型：磁盘 -> [(tag_id, 比例)]
Individual = List[List[Tuple[int, float]]]

def create_individual() -> Individual:
    """创建一个随机的解决方案"""
    # 初始化结果，每个磁盘包含空列表
    individual = [[] for _ in range(NUM_DISKS)]
    
    # 为每个标签分配空间
    for tag_id in range(len(TAGS)):
        # 决定分割数量 (1到MAX_SPLITS_PER_TAG)
        num_splits = random.randint(1, min(MAX_SPLITS_PER_TAG, NUM_DISKS))
        
        # 随机选择磁盘
        disk_indices = random.sample(range(NUM_DISKS), num_splits)
        
        # 分配随机比例
        proportions = [random.random() for _ in range(num_splits)]
        proportions_sum = sum(proportions)
        proportions = [p / proportions_sum for p in proportions]
        
        # 添加到选定的磁盘上
        for i, disk_idx in enumerate(disk_indices):
            individual[disk_idx].append((tag_id, proportions[i]))
    
    # 如果有磁盘的标签数量超过上限，修复解决方案
    for disk_idx in range(NUM_DISKS):
        while len(individual[disk_idx]) > MAX_TAGS_PER_DISK:
            # 随机选择一个标签移除
            tag_to_remove_idx = random.randrange(len(individual[disk_idx]))
            tag_id, proportion = individual[disk_idx].pop(tag_to_remove_idx)
            
            # 找一个标签数量少的磁盘
            valid_disks = [i for i in range(NUM_DISKS) 
                          if len(individual[i]) < MAX_TAGS_PER_DISK and
                             not any(t[0] == tag_id for t in individual[i])]
            
            if valid_disks:
                target_disk = random.choice(valid_disks)
                individual[target_disk].append((tag_id, proportion))
            else:
                # 如果没有可用磁盘，重新分配这个比例到已有该标签的磁盘
                disks_with_tag = [i for i in range(NUM_DISKS) 
                                 if any(t[0] == tag_id for t in individual[i])]
                
                if disks_with_tag:
                    target_disk = random.choice(disks_with_tag)
                    # 找到这个标签在目标磁盘中的位置
                    for j, (t_id, prop) in enumerate(individual[target_disk]):
                        if t_id == tag_id:
                            individual[target_disk][j] = (tag_id, prop + proportion)
                            break
    
    # 确保所有磁盘至少有一个标签
    for disk_idx in range(NUM_DISKS):
        if not individual[disk_idx]:
            # 从标签较多的磁盘借一个
            donor_disk = max(range(NUM_DISKS), key=lambda i: len(individual[i]))
            if len(individual[donor_disk]) > 1:  # 确保捐赠后还有标签
                tag_to_move = individual[donor_disk].pop(random.randrange(len(individual[donor_disk])))
                individual[disk_idx].append(tag_to_move)
    
    # 归一化每个标签的比例总和为1
    tag_proportions = {}
    for disk_idx in range(NUM_DISKS):
        for tag_id, proportion in individual[disk_idx]:
            if tag_id not in tag_proportions:
                tag_proportions[tag_id] = []
            tag_proportions[tag_id].append((disk_idx, proportion))
    
    for tag_id, props in tag_proportions.items():
        total = sum(p for _, p in props)
        for i, (disk_idx, _) in enumerate(props):
            # 更新磁盘上的比例
            for j, (t_id, _) in enumerate(individual[disk_idx]):
                if t_id == tag_id:
                    individual[disk_idx][j] = (tag_id, props[i][1] / total)
                    break
    
    return individual

def calculate_disk_loads(individual: Individual) -> np.ndarray:
    """计算每个磁盘的对象数量"""
    disk_loads = np.zeros(NUM_DISKS)
    
    for disk_idx in range(NUM_DISKS):
        for tag_id, proportion in individual[disk_idx]:
            disk_loads[disk_idx] += TAGS[tag_id] * proportion
    
    return disk_loads

def calculate_disk_reads_per_timepoint(individual: Individual) -> np.ndarray:
    """计算每个时间点每个磁盘的读取量"""
    # 结果形状: (时间点数量, 磁盘数量)
    disk_reads = np.zeros((READS_PER_TIMEPOINT.shape[0], NUM_DISKS))
    
    for disk_idx in range(NUM_DISKS):
        for tag_id, proportion in individual[disk_idx]:
            for time_idx in range(READS_PER_TIMEPOINT.shape[0]):
                disk_reads[time_idx, disk_idx] += READS_PER_TIMEPOINT[time_idx, tag_id] * proportion
    
    return disk_reads

def calculate_disk_read_variance(individual: Individual) -> np.ndarray:
    """计算每个磁盘在所有时间点的读取量方差"""
    disk_reads = calculate_disk_reads_per_timepoint(individual)
    # 计算每个磁盘在不同时间点的读取量方差
    return np.var(disk_reads, axis=0)

def evaluate_fitness(individual: Individual) -> float:
    """评估适应度，越低越好"""
    # 计算对象负载
    disk_loads = calculate_disk_loads(individual)
    # 负载均衡性，使用变异系数代替标准差，以避免大小标签的影响
    load_mean = np.mean(disk_loads)
    load_std = np.std(disk_loads)
    load_cv = load_std / load_mean if load_mean > 0 else float('inf')
    
    # 计算每个磁盘在所有时间点的读取量方差
    disk_read_variances = calculate_disk_read_variance(individual)
    # 读取方差均值
    read_variance_mean = np.mean(disk_read_variances)
    
    # 负载最大差异（最大值和最小值之间的差异）
    load_range = np.max(disk_loads) - np.min(disk_loads)
    load_range_normalized = load_range / load_mean if load_mean > 0 else float('inf')
    
    # 方差的最大值和最小值之间的差异
    variance_range = np.max(disk_read_variances) - np.min(disk_read_variances)
    
    # 总分 (越小越好) - 增加了最大负载差异和方差差异的惩罚
    fitness = (
        load_cv * 0.4 + 
        read_variance_mean * 0.3 + 
        load_range_normalized * 0.2 + 
        variance_range * 0.1
    )
    
    return fitness

def batch_evaluate_fitness(individuals: List[Individual]) -> List[float]:
    """批量评估多个个体的适应度，用于并行计算"""
    return [evaluate_fitness(ind) for ind in individuals]

def crossover(parent1: Individual, parent2: Individual) -> Individual:
    """交叉两个父代，生成一个新的子代"""
    child = [[] for _ in range(NUM_DISKS)]
    
    # 改进的交叉策略：半均匀交叉
    for disk_idx in range(NUM_DISKS):
        if random.random() < 0.5:
            child[disk_idx] = parent1[disk_idx].copy()
        else:
            child[disk_idx] = parent2[disk_idx].copy()
    
    # 验证并修复解决方案
    # 1. 检查每个标签的分配是否完整
    tag_coverage = {tag_id: 0.0 for tag_id in range(len(TAGS))}
    tag_disk_map = {tag_id: [] for tag_id in range(len(TAGS))}
    
    for disk_idx in range(NUM_DISKS):
        for tag_id, proportion in child[disk_idx]:
            tag_coverage[tag_id] += proportion
            tag_disk_map[tag_id].append(disk_idx)
    
    # 修复不完整的标签分配
    for tag_id, coverage in tag_coverage.items():
        if abs(coverage - 1.0) > 0.001:  # 如果总和不接近1
            if coverage == 0:  # 标签完全未分配
                # 随机选择一个磁盘
                disk_idx = random.randrange(NUM_DISKS)
                child[disk_idx].append((tag_id, 1.0))
            else:  # 部分分配
                # 归一化
                for disk_idx in tag_disk_map[tag_id]:
                    for i, (t_id, prop) in enumerate(child[disk_idx]):
                        if t_id == tag_id:
                            child[disk_idx][i] = (tag_id, prop / coverage)
    
    # 2. 处理磁盘标签超过上限的情况
    for disk_idx in range(NUM_DISKS):
        while len(child[disk_idx]) > MAX_TAGS_PER_DISK:
            # 找出适应度最差的标签（贪心策略）
            worst_tag_idx = -1
            worst_tag_contribution = -float('inf')
            
            for i, (tag_id, proportion) in enumerate(child[disk_idx]):
                # 模拟移除这个标签后的情况
                temp_disk = [t for j, t in enumerate(child[disk_idx]) if j != i]
                disk_load_before = sum(TAGS[t_id] * prop for t_id, prop in child[disk_idx])
                disk_load_after = sum(TAGS[t_id] * prop for t_id, prop in temp_disk)
                
                # 标签对负载的贡献
                contribution = abs(disk_load_after - disk_load_before)
                
                if contribution > worst_tag_contribution:
                    worst_tag_contribution = contribution
                    worst_tag_idx = i
            
            # 如果没有找到最差标签，则随机选择一个
            if worst_tag_idx == -1:
                worst_tag_idx = random.randrange(len(child[disk_idx]))
            
            tag_id, proportion = child[disk_idx].pop(worst_tag_idx)
            
            # 找一个标签数量少的磁盘
            valid_disks = [i for i in range(NUM_DISKS) 
                          if len(child[i]) < MAX_TAGS_PER_DISK]
            
            if valid_disks:
                # 选择负载最小的磁盘
                target_disk = min(valid_disks, key=lambda i: sum(TAGS[t_id] * prop for t_id, prop in child[i]))
                
                # 检查目标磁盘是否已有该标签
                has_tag = False
                for i, (t_id, prop) in enumerate(child[target_disk]):
                    if t_id == tag_id:
                        child[target_disk][i] = (tag_id, prop + proportion)
                        has_tag = True
                        break
                
                if not has_tag:
                    child[target_disk].append((tag_id, proportion))
    
    # 3. 确保所有磁盘至少有一个标签
    for disk_idx in range(NUM_DISKS):
        if not child[disk_idx]:
            # 从标签较多的磁盘借一个
            donor_disk = max(range(NUM_DISKS), key=lambda i: len(child[i]))
            if len(child[donor_disk]) > 1:  # 确保捐赠后还有标签
                # 选择贡献最小的标签
                tag_idx = min(range(len(child[donor_disk])), 
                              key=lambda i: TAGS[child[donor_disk][i][0]] * child[donor_disk][i][1])
                tag_to_move = child[donor_disk].pop(tag_idx)
                child[disk_idx].append(tag_to_move)
    
    return child

def calculate_mutation_rate(generation: int, max_generations: int) -> float:
    """根据当前代数计算动态变异率"""
    # 线性衰减
    return MUTATION_RATE_START - (MUTATION_RATE_START - MUTATION_RATE_END) * generation / max_generations

def mutate(individual: Individual, mutation_rate: float) -> Individual:
    """变异个体，使用动态变异率"""
    mutated = [disk.copy() for disk in individual]
    
    # 选择一个随机磁盘
    disk_idx = random.randrange(NUM_DISKS)
    
    # 随机选择突变类型
    mutation_type = random.random()
    
    if mutation_type < 0.33 and len(mutated[disk_idx]) > 0:  # 移除一个标签
        if len(mutated[disk_idx]) > MIN_TAGS_PER_DISK:
            # 选择负载贡献最大的标签来移除
            tag_idx = max(range(len(mutated[disk_idx])), 
                         key=lambda i: TAGS[mutated[disk_idx][i][0]] * mutated[disk_idx][i][1])
            tag_id, proportion = mutated[disk_idx].pop(tag_idx)
            
            # 找另一个磁盘添加或增加这个标签
            # 选择负载最小的磁盘
            target_disks = [i for i in range(NUM_DISKS) if i != disk_idx]
            target_disk = min(target_disks, 
                              key=lambda i: sum(TAGS[t_id] * prop for t_id, prop in mutated[i]))
            
            # 检查目标磁盘是否已有该标签
            has_tag = False
            for i, (t_id, prop) in enumerate(mutated[target_disk]):
                if t_id == tag_id:
                    mutated[target_disk][i] = (tag_id, prop + proportion)
                    has_tag = True
                    break
            
            if not has_tag and len(mutated[target_disk]) < MAX_TAGS_PER_DISK:
                mutated[target_disk].append((tag_id, proportion))
            elif not has_tag:
                # 如果目标磁盘已满，将比例分配给其他包含此标签的磁盘
                disks_with_tag = [i for i in range(NUM_DISKS) 
                                 if i != disk_idx and any(t[0] == tag_id for t in mutated[i])]
                
                if disks_with_tag:
                    redistribution_disk = random.choice(disks_with_tag)
                    for i, (t_id, prop) in enumerate(mutated[redistribution_disk]):
                        if t_id == tag_id:
                            mutated[redistribution_disk][i] = (tag_id, prop + proportion)
                            break
    
    elif mutation_type < 0.66:  # 修改标签比例
        if mutated[disk_idx]:
            # 选择较大的标签来调整比例
            tags_with_size = [(i, TAGS[t_id] * prop) for i, (t_id, prop) in enumerate(mutated[disk_idx])]
            tag_idx = max(range(len(tags_with_size)), key=lambda i: tags_with_size[i][1])
            
            tag_id, old_proportion = mutated[disk_idx][tag_idx]
            
            # 随机调整比例，但保持总和为1
            # 高变异率时变化大，低变异率时变化小
            change = (random.random() - 0.5) * 0.4 * mutation_rate  
            new_proportion = max(0.01, min(0.99, old_proportion + change))
            delta = new_proportion - old_proportion
            
            # 找出其他包含此标签的磁盘
            other_disks_with_tag = []
            for i in range(NUM_DISKS):
                if i != disk_idx:
                    for j, (t_id, prop) in enumerate(mutated[i]):
                        if t_id == tag_id:
                            other_disks_with_tag.append((i, j, prop))
            
            if other_disks_with_tag:
                # 从其他磁盘减去增加的比例
                total_other_proportion = sum(prop for _, _, prop in other_disks_with_tag)
                for i, j, prop in other_disks_with_tag:
                    adjustment = (delta * prop) / total_other_proportion
                    new_other_prop = max(0.01, prop - adjustment)
                    mutated[i][j] = (tag_id, new_other_prop)
                
                # 重新归一化
                total = new_proportion + sum(max(0.01, mutated[i][j][1]) 
                                           for i, j, _ in other_disks_with_tag)
                
                mutated[disk_idx][tag_idx] = (tag_id, new_proportion / total)
                for i, j, _ in other_disks_with_tag:
                    t_id, prop = mutated[i][j]
                    mutated[i][j] = (t_id, prop / total)
    
    else:  # 交换两个磁盘之间的标签
        if mutated[disk_idx]:
            # 选择负载最大和最小的两个磁盘交换标签
            disk_loads = [sum(TAGS[t_id] * prop for t_id, prop in disk) for disk in mutated]
            max_load_disk = max(range(NUM_DISKS), key=lambda i: disk_loads[i])
            min_load_disk = min(range(NUM_DISKS), key=lambda i: disk_loads[i])
            
            if mutated[max_load_disk] and mutated[min_load_disk]:
                # 从高负载磁盘选择一个大标签
                tag_idx1 = max(range(len(mutated[max_load_disk])), 
                              key=lambda i: TAGS[mutated[max_load_disk][i][0]] * mutated[max_load_disk][i][1])
                
                # 从低负载磁盘选择一个小标签
                tag_idx2 = min(range(len(mutated[min_load_disk])), 
                              key=lambda i: TAGS[mutated[min_load_disk][i][0]] * mutated[min_load_disk][i][1])
                
                # 交换标签
                mutated[max_load_disk][tag_idx1], mutated[min_load_disk][tag_idx2] = \
                    mutated[min_load_disk][tag_idx2], mutated[max_load_disk][tag_idx1]
    
    return mutated

def create_offspring(parent_indices: List[int], population: List[Individual], 
                    fitness_scores: List[float], mutation_rate: float) -> Individual:
    """创建一个后代，用于并行处理"""
    # 锦标赛选择两个父代
    parent1_idx = min(random.sample(parent_indices, TOURNAMENT_SIZE), 
                     key=lambda i: fitness_scores[i])
    parent2_idx = min(random.sample(parent_indices, TOURNAMENT_SIZE), 
                     key=lambda i: fitness_scores[i])
    
    parent1 = population[parent1_idx]
    parent2 = population[parent2_idx]
    
    # 应用交叉
    if random.random() < CROSSOVER_RATE:
        child = crossover(parent1, parent2)
    else:
        child = parent1.copy() if random.random() < 0.5 else parent2.copy()
    
    # 应用变异
    if random.random() < mutation_rate:
        child = mutate(child, mutation_rate)
    
    return child

def genetic_algorithm() -> Tuple[Individual, float]:
    """实现遗传算法，返回最佳个体及其适应度"""
    # 初始化种群
    population = [create_individual() for _ in range(POPULATION_SIZE)]
    
    # 记录每代的最佳适应度，用于绘图
    best_fitness_history = []
    avg_fitness_history = []
    
    # 收敛计数器
    stagnation_counter = 0
    best_fitness_ever = float('inf')
    best_individual_ever = None
    
    # 创建线程池
    with concurrent.futures.ThreadPoolExecutor(max_workers=THREADS) as executor:
        # 主循环
        for generation in range(NUM_GENERATIONS):
            # 计算当前代的变异率
            current_mutation_rate = calculate_mutation_rate(generation, NUM_GENERATIONS)
            
            # 并行评估适应度
            chunk_size = max(1, POPULATION_SIZE // THREADS)
            chunks = [population[i:i+chunk_size] for i in range(0, POPULATION_SIZE, chunk_size)]
            fitness_chunks = list(executor.map(batch_evaluate_fitness, chunks))
            fitness_scores = [score for chunk in fitness_chunks for score in chunk]
            
            # 记录这一代的统计信息
            best_idx = np.argmin(fitness_scores)
            best_fitness = fitness_scores[best_idx]
            best_individual = population[best_idx]
            avg_fitness = np.mean(fitness_scores)
            
            best_fitness_history.append(best_fitness)
            avg_fitness_history.append(avg_fitness)
            
            # 检查是否有新的最佳解
            if best_fitness < best_fitness_ever:
                best_fitness_ever = best_fitness
                best_individual_ever = best_individual
                stagnation_counter = 0
            else:
                stagnation_counter += 1
            
            # 每10代输出一次进度
            if generation % 10 == 0:
                print(f"Generation {generation}: Best Fitness = {best_fitness:.4f}, Average Fitness = {avg_fitness:.4f}, Mutation Rate = {current_mutation_rate:.4f}")
            
            # 检查是否应该提前停止
            if stagnation_counter >= CONVERGENCE_THRESHOLD:
                print(f"Early stopping at generation {generation} due to no improvement for {CONVERGENCE_THRESHOLD} generations")
                break
            
            # 保留精英
            elite_indices = np.argsort(fitness_scores)[:ELITE_SIZE]
            new_population = [population[i] for i in elite_indices]
            
            # 生成新一代
            parent_indices = list(range(POPULATION_SIZE))
            offspring_needed = POPULATION_SIZE - ELITE_SIZE
            
            # 并行创建后代
            offspring_futures = [
                executor.submit(create_offspring, parent_indices, population, fitness_scores, current_mutation_rate)
                for _ in range(offspring_needed)
            ]
            
            # 收集后代
            for future in concurrent.futures.as_completed(offspring_futures):
                new_population.append(future.result())
            
            # 更新种群
            population = new_population
    
    # 使用整个进化过程中找到的最佳个体
    best_individual = best_individual_ever
    best_fitness = best_fitness_ever
    
    # 绘制进化曲线
    plt.figure(figsize=(12, 6))
    plt.plot(best_fitness_history, label='Best Fitness')
    plt.plot(avg_fitness_history, label='Average Fitness')
    plt.xlabel('Generation')
    plt.ylabel('Fitness (lower is better)')
    plt.title('Genetic Algorithm Evolution Process')
    plt.legend()
    plt.grid(True)
    plt.savefig('evolution_curve.png')
    
    return best_individual, best_fitness

def print_solution(individual: Individual) -> None:
    """打印解决方案详情"""
    print("\n最优分配方案:")
    
    # 计算每个磁盘的对象数量
    disk_loads = calculate_disk_loads(individual)
    total_objects = sum(TAGS)
    
    # 计算每个磁盘在所有时间点的读取量及方差
    disk_reads = calculate_disk_reads_per_timepoint(individual)
    disk_read_variances = calculate_disk_read_variance(individual)
    
    # 输出每个磁盘的详细信息
    for disk_idx in range(NUM_DISKS):
        print(f"\n磁盘 {disk_idx}:")
        print(f"  标签数量: {len(individual[disk_idx])}")
        print(f"  对象数量: {disk_loads[disk_idx]:.1f} ({disk_loads[disk_idx]/total_objects*100:.1f}%)")
        print(f"  读取方差: {disk_read_variances[disk_idx]:.2f}")
        
        print("  标签分配:")
        for tag_id, proportion in individual[disk_idx]:
            print(f"    标签 {tag_id}: {TAGS[tag_id]} 个对象 x {proportion:.2f} = {TAGS[tag_id] * proportion:.1f} 个对象")
        
        print("  各时间点读取量:")
        for time_idx in range(disk_reads.shape[0]):
            print(f"    时间点 {time_idx}: {disk_reads[time_idx, disk_idx]:.1f}")
    
    # 输出统计信息
    print("\n统计信息:")
    print(f"总对象数: {total_objects}")
    print(f"平均每个磁盘对象数: {np.mean(disk_loads):.1f}")
    print(f"对象分布标准差: {np.std(disk_loads):.1f}")
    print(f"对象分布变异系数: {np.std(disk_loads)/np.mean(disk_loads):.4f}")
    print(f"最小磁盘对象数: {np.min(disk_loads):.1f}")
    print(f"最大磁盘对象数: {np.max(disk_loads):.1f}")
    print(f"平均读取方差: {np.mean(disk_read_variances):.2f}")
    print(f"读取方差最小值: {np.min(disk_read_variances):.2f}")
    print(f"读取方差最大值: {np.max(disk_read_variances):.2f}")

def save_solution_to_file(individual: Individual, fitness: float, filename: str = "solution.txt") -> None:
    """将解决方案保存到文件"""
    with open(filename, 'w') as f:
        f.write("最优分配方案:\n")
        
        # 计算每个磁盘的对象数量
        disk_loads = calculate_disk_loads(individual)
        total_objects = sum(TAGS)
        
        # 计算每个磁盘在所有时间点的读取量及方差
        disk_reads = calculate_disk_reads_per_timepoint(individual)
        disk_read_variances = calculate_disk_read_variance(individual)
        
        # 写入适应度
        f.write(f"适应度分数: {fitness:.6f}\n\n")
        
        # 输出每个磁盘的详细信息
        for disk_idx in range(NUM_DISKS):
            f.write(f"磁盘 {disk_idx}:\n")
            f.write(f"  标签数量: {len(individual[disk_idx])}\n")
            f.write(f"  对象数量: {disk_loads[disk_idx]:.1f} ({disk_loads[disk_idx]/total_objects*100:.1f}%)\n")
            f.write(f"  读取方差: {disk_read_variances[disk_idx]:.2f}\n")
            
            f.write("  标签分配:\n")
            for tag_id, proportion in individual[disk_idx]:
                f.write(f"    标签 {tag_id}: {TAGS[tag_id]} 个对象 x {proportion:.4f} = {TAGS[tag_id] * proportion:.1f} 个对象\n")
            
            f.write("  各时间点读取量:\n")
            for time_idx in range(disk_reads.shape[0]):
                f.write(f"    时间点 {time_idx}: {disk_reads[time_idx, disk_idx]:.1f}\n")
            
            f.write("\n")
        
        # 输出统计信息
        f.write("统计信息:\n")
        f.write(f"总对象数: {total_objects}\n")
        f.write(f"平均每个磁盘对象数: {np.mean(disk_loads):.1f}\n")
        f.write(f"对象分布标准差: {np.std(disk_loads):.1f}\n")
        f.write(f"对象分布变异系数: {np.std(disk_loads)/np.mean(disk_loads):.4f}\n")
        f.write(f"最小磁盘对象数: {np.min(disk_loads):.1f}\n")
        f.write(f"最大磁盘对象数: {np.max(disk_loads):.1f}\n")
        f.write(f"平均读取方差: {np.mean(disk_read_variances):.2f}\n")
        f.write(f"读取方差最小值: {np.min(disk_read_variances):.2f}\n")
        f.write(f"读取方差最大值: {np.max(disk_read_variances):.2f}\n")

def main():
    """主函数"""
    start_time = time.time()
    
    print(f"开始运行遗传算法... (使用 {THREADS} 个线程)")
    best_individual, best_fitness = genetic_algorithm()
    
    end_time = time.time()
    print(f"\n遗传算法运行完成，耗时 {end_time - start_time:.2f} 秒")
    print(f"最佳适应度分数: {best_fitness:.6f}")
    
    # 打印详细解决方案
    print_solution(best_individual)
    
    # 注释掉保存解决方案到文件的代码
    # save_solution_to_file(best_individual, best_fitness)
    # print("\n解决方案已保存到 solution.txt")

if __name__ == "__main__":
    main() 