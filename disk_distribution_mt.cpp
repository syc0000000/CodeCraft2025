#include <algorithm>
#include <chrono>
#include <cmath>
#include <functional>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <queue>
#include <random>
#include <thread>
#include <unordered_map>
#include <vector>

using namespace std;

// 定义tag数组
const vector<int> tags = {1968, 799,  2121, 1849, 944, 502,  1933, 1507,
                          494,  1540, 631,  1715, 434, 1107, 545,  2196};

// 定义分配方案的数据结构
struct Split {
  int disk_idx;
  int portion; // 使用整数表示百分比
  Split(int d, int p) : disk_idx(d), portion(p) {}
};

using Distribution = unordered_map<int, vector<Split>>;

// 用于线程同步的互斥锁和全局最优解
mutex mtx;
double global_best_variance = INFINITY;
Distribution global_best_distribution;

// 计算磁盘负载
vector<double> calculate_disk_loads(const Distribution &distribution) {
  vector<double> disk_loads(10, 0.0);
  for (const auto &[tag_idx, splits] : distribution) {
    double tag_size = tags[tag_idx];
    for (const auto &split : splits) {
      disk_loads[split.disk_idx] += tag_size * split.portion / 100.0;
    }
  }
  return disk_loads;
}

// 检查分配是否有效
bool is_valid_distribution(const Distribution &distribution) {
  vector<int> disk_tags(10, 0);

  // 检查每个磁盘的tag数量和每个tag的总比例
  for (const auto &[tag_idx, splits] : distribution) {
    int total_portion = 0;
    for (const auto &split : splits) {
      if (split.disk_idx >= 10)
        return false;
      disk_tags[split.disk_idx]++;
      total_portion += split.portion;
    }
    if (total_portion != 100)
      return false;
  }

  // 检查每个磁盘的tag数量是否在1-4之间
  for (int count : disk_tags) {
    if (count < 1 || count > 4)
      return false;
  }

  return true;
}

// 计算评分（考虑多个因素）
double calculate_score(const vector<double> &disk_loads) {
  double total_load = 0.0;
  double min_load = INFINITY;
  double max_load = 0.0;

  for (double load : disk_loads) {
    total_load += load;
    min_load = min(min_load, load);
    max_load = max(max_load, load);
  }

  double mean = total_load / 10;
  double variance = 0.0;
  double max_deviation = 0.0;

  for (double load : disk_loads) {
    double diff = load - mean;
    variance += diff * diff;
    max_deviation = max(max_deviation, abs(diff));
  }

  // 调整权重以更注重均衡性
  return variance / 10 + max_deviation * 3 + (max_load - min_load) * 2;
}

// 生成所有可能的分割方案
vector<vector<int>> generate_all_portions(int num_splits) {
  vector<vector<int>> result;
  vector<int> current;

  function<void(vector<int> &, int, int, int)> generate_recursive =
      [&](vector<int> &current, int remaining, int parts_left, int min_val) {
        if (parts_left == 1) {
          if (remaining >= min_val) {
            current.push_back(remaining);
            result.push_back(current);
            current.pop_back();
          }
          return;
        }

        // 使用1%的步长
        for (int i = min_val; i <= remaining - min_val * (parts_left - 1);
             i += 1) {
          current.push_back(i);
          generate_recursive(current, remaining - i, parts_left - 1, min_val);
          current.pop_back();
        }
      };

  generate_recursive(current, 100, num_splits, 1); // 最小分割为1%
  return result;
}

// 模拟退火算法的温度函数
double temperature(int iteration, int max_iterations) {
  // 使用更缓慢的冷却速度
  return pow(0.99, iteration * max_iterations / 1000000.0);
}

// 初始化一个基本的分配方案
Distribution initialize_distribution() {
  Distribution dist;
  vector<double> disk_loads(10, 0.0);
  vector<int> disk_tag_counts(10, 0);

  // 按大小排序tags
  vector<pair<int, int>> sorted_tags;
  for (int i = 0; i < tags.size(); ++i) {
    sorted_tags.emplace_back(tags[i], i);
  }
  sort(sorted_tags.rbegin(), sorted_tags.rend());

  // 贪心分配
  for (const auto &[size, tag_idx] : sorted_tags) {
    // 找到最适合的磁盘
    int best_disk = -1;
    double min_score = INFINITY;

    for (int disk = 0; disk < 10; ++disk) {
      if (disk_tag_counts[disk] >= 4)
        continue;

      // 尝试分配到这个磁盘
      disk_loads[disk] += size;
      double score = calculate_score(disk_loads);
      disk_loads[disk] -= size;

      if (score < min_score) {
        min_score = score;
        best_disk = disk;
      }
    }

    vector<Split> splits;
    splits.emplace_back(best_disk, 100);
    dist[tag_idx] = splits;

    disk_loads[best_disk] += size;
    disk_tag_counts[best_disk]++;
  }

  return dist;
}

// 尝试优化分配方案
void optimize_distribution(int thread_id, int num_threads) {
  random_device rd;
  mt19937 gen(rd());
  uniform_int_distribution<> dis(0, 9);
  uniform_int_distribution<> tag_dis(0, tags.size() - 1);

  Distribution current_dist = initialize_distribution();
  const int iterations_per_thread = 100000; // 大幅增加迭代次数

  // 预生成所有可能的分割方案
  vector<vector<vector<int>>> all_portions;
  for (int i = 1; i <= 4; ++i) {
    all_portions.push_back(generate_all_portions(i));
  }

  // 记录线程局部最优解
  double local_best_score = calculate_score(calculate_disk_loads(current_dist));
  Distribution local_best_dist = current_dist;
  int stagnation_count = 0;

  for (int iter = 0; iter < iterations_per_thread; ++iter) {
    double temp = temperature(iter, iterations_per_thread);
    Distribution new_dist = current_dist;

    // 随机选择1-4个tag进行优化
    int num_tags_to_optimize = (iter % 4) + 1;
    vector<int> selected_tags;
    for (int i = 0; i < num_tags_to_optimize; ++i) {
      int tag_idx;
      do {
        tag_idx = tag_dis(gen);
      } while (find(selected_tags.begin(), selected_tags.end(), tag_idx) !=
               selected_tags.end());
      selected_tags.push_back(tag_idx);
    }

    // 对选中的tag尝试不同的分割方案
    for (int tag_idx : selected_tags) {
      vector<Split> &splits = new_dist[tag_idx];

      // 随机选择分割数量
      int num_splits = (gen() % 4) + 1;
      const auto &portions = all_portions[num_splits - 1];

      if (portions.empty())
        continue;

      // 随机选择一个分割方案
      const auto &selected_portions = portions[gen() % portions.size()];

      splits.clear();
      vector<int> available_disks;
      for (int i = 0; i < 10; ++i) {
        available_disks.push_back(i);
      }
      shuffle(available_disks.begin(), available_disks.end(), gen);

      for (int i = 0; i < num_splits; ++i) {
        splits.emplace_back(available_disks[i], selected_portions[i]);
      }
    }

    if (is_valid_distribution(new_dist)) {
      vector<double> disk_loads = calculate_disk_loads(new_dist);
      double new_score = calculate_score(disk_loads);
      double current_score =
          calculate_score(calculate_disk_loads(current_dist));

      // 模拟退火决策
      double delta = new_score - current_score;
      if (delta < 0 ||
          (temp > 0 &&
           exp(-delta / temp) > uniform_real_distribution<>(0, 1)(gen))) {
        current_dist = new_dist;
        current_score = new_score;

        // 更新局部最优解
        if (new_score < local_best_score) {
          local_best_score = new_score;
          local_best_dist = new_dist;
          stagnation_count = 0;
        }

        // 更新全局最优解
        lock_guard<mutex> lock(mtx);
        if (new_score < global_best_variance) {
          global_best_variance = new_score;
          global_best_distribution = new_dist;
        }
      }
    }

    // 如果局部最优解长时间没有改善，重新初始化
    stagnation_count++;
    if (stagnation_count > 10000) {
      current_dist = initialize_distribution();
      stagnation_count = 0;
    }
  }
}

// 多线程优化
pair<Distribution, double> find_optimal_distribution_mt() {
  const int num_threads = thread::hardware_concurrency();
  vector<thread> threads;

  // 初始化全局最优解
  global_best_distribution = initialize_distribution();
  global_best_variance =
      calculate_score(calculate_disk_loads(global_best_distribution));

  // 启动多个线程进行优化
  for (int i = 0; i < num_threads; ++i) {
    threads.emplace_back(optimize_distribution, i, num_threads);
  }

  // 等待所有线程完成
  for (auto &t : threads) {
    t.join();
  }

  return {global_best_distribution, global_best_variance};
}

// 打印结果
void print_result(const Distribution &distribution) {
  if (distribution.empty()) {
    cout << "未找到有效解" << endl;
    return;
  }

  vector<double> disk_loads = calculate_disk_loads(distribution);
  vector<vector<pair<int, double>>> disk_tags(10);

  for (const auto &[tag_idx, splits] : distribution) {
    for (const auto &split : splits) {
      double portion = split.portion / 100.0;
      disk_tags[split.disk_idx].emplace_back(tag_idx, portion);
    }
  }

  // 计算统计信息
  double total_load = 0.0;
  double min_load = INFINITY;
  double max_load = 0.0;

  for (double load : disk_loads) {
    total_load += load;
    min_load = min(min_load, load);
    max_load = max(max_load, load);
  }

  double mean_load = total_load / 10;
  double variance = 0.0;
  for (double load : disk_loads) {
    variance += (load - mean_load) * (load - mean_load);
  }
  variance /= 10;

  cout << "\n负载统计：" << endl;
  cout << "平均负载: " << fixed << setprecision(2) << mean_load << endl;
  cout << "最小负载: " << min_load << endl;
  cout << "最大负载: " << max_load << endl;
  cout << "负载差异: " << max_load - min_load << endl;
  cout << "方差: " << variance << endl;
  cout << "优化目标值: " << global_best_variance << endl;

  cout << "\n最优分配方案：" << endl;
  for (int disk_idx = 0; disk_idx < 10; ++disk_idx) {
    cout << "\n磁盘 " << disk_idx << ":" << endl;
    cout << "总负载: " << disk_loads[disk_idx] << endl;
    cout << "Tags:" << endl;
    for (const auto &[tag_idx, portion] : disk_tags[disk_idx]) {
      cout << "  Tag " << tag_idx << ": " << portion << " * " << tags[tag_idx]
           << " = " << portion * tags[tag_idx] << endl;
    }
  }
}

int main() {
  cout << "使用 " << thread::hardware_concurrency() << " 个线程进行计算"
       << endl;

  auto start_time = chrono::high_resolution_clock::now();

  auto [best_distribution, best_variance] = find_optimal_distribution_mt();

  auto end_time = chrono::high_resolution_clock::now();
  auto duration =
      chrono::duration_cast<chrono::milliseconds>(end_time - start_time);

  print_result(best_distribution);

  cout << "\n程序运行时间: " << duration.count() << " 毫秒" << endl;

  return 0;
}