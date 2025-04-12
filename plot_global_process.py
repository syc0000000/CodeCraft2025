import math

import matplotlib.pyplot as plt


def read_input_file(filename):
    """从文件读取输入数据的函数"""
    with open(filename, "r") as f:
        # 读取第一行参数
        header = f.readline().strip()
        T, M, N, V, G, C = map(int, header.split())

        # 读取删除频率数据
        fre_del = []
        for _ in range(M):
            line = f.readline().strip()
            fre_del.append(list(map(int, line.split())))

        # 读取写入频率数据
        fre_write = []
        for _ in range(M):
            line = f.readline().strip()
            fre_write.append(list(map(int, line.split())))

        # 跳过读取频率数据（不需要使用）
        fre_read = []
        for _ in range(M):
            line = f.readline().strip()
            fre_read.append(list(map(int, line.split())))

    return T, M, N, V, G, fre_del, fre_write, fre_read


def calculate_cumulative(data):
    """计算累积值的函数"""
    cumulative = [0] * (len(data) + 1)
    for i in range(1, len(data) + 1):
        cumulative[i] = cumulative[i - 1] + data[i - 1]
    return cumulative

def generate_plot(data, ylabel, title, filename, cumulative_mode=True):
    """通用绘图函数"""
    colors = [
        "#F38BA8",
        "#EBA0AC",
        "#FAB387",
        "#F9E2AF",
        "#A6E3A1",
        "#94E2D5",
        "#89DCEB",
        "#74C7EC",
        "#89B4FA",
        "#B4BEFE",
        "#CBA6F7",
        "#F5C2E7",
        "#F5E0DC",
        "#F2CDCD",
        "#CDD6F4",
        "#BAC2DE",
    ]

    plt.figure(figsize=(25, 12))
    max_val = 0
    for tag_idx in range(M):
        if cumulative_mode:
            # 累计模式
            cumulative = [0.0] * (T + 105 + 1)

            # 处理时间窗口
            for j_idx in range(math.ceil(T / 1800)):
                start = j_idx * 1800 + 1
                end = min((j_idx + 1) * 1800, T)
                window_size = end - start + 1

                if window_size == 0:
                    continue

                delta = data[tag_idx][j_idx]
                delta_per_ts = delta / window_size

                for ts in range(start, end + 1):
                    cumulative[ts] = cumulative[ts - 1] + delta_per_ts

            # 处理无操作时间段
            for ts in range(T + 1, T + 105 + 1):
                cumulative[ts] = cumulative[ts - 1]
        else:
            # 非累计模式
            cumulative = [0.0] * (T + 105 + 1)
            
            # 处理时间窗口
            for j_idx in range(math.ceil(T / 1800)):
                start = j_idx * 1800 + 1
                end = min((j_idx + 1) * 1800, T)
                window_size = end - start + 1

                if window_size == 0:
                    continue

                delta = data[tag_idx][j_idx]
                delta_per_ts = delta / window_size

                for ts in range(start, end + 1):
                    cumulative[ts] = delta_per_ts

        # 跟踪最大值
        local_max = max(cumulative)
        if local_max > max_val:
            max_val = local_max

        # 智能打点配置
        marker_interval = max(1, len(cumulative) // 50)  # 自动计算显示密度

        plt.plot(
            range(T + 105 + 1),
            cumulative,
            color=colors[tag_idx % len(colors)],  # 使用指定颜色
            linewidth=1.5,
            marker="o",
            markersize=3,
            markevery=marker_interval,
            markerfacecolor=colors[tag_idx % len(colors)],
            markeredgecolor=colors[tag_idx % len(colors)],
            label=f"Tag {tag_idx+1}",
        )

    # 设置纵轴范围
    y_upper = max_val * 1.1
    plt.xlim(0, T + 105)
    plt.ylim(0, y_upper)
    plt.xlabel("Time Slice", fontsize=14)
    plt.ylabel(ylabel, fontsize=14)
    plt.title(title, fontsize=16)
    plt.legend(bbox_to_anchor=(1.02, 0.98), loc="upper left", ncol=2 if M > 10 else 1)
    plt.grid(True, linestyle="--", alpha=0.6)
    plt.tight_layout()
    plt.savefig(filename, bbox_inches="tight")
    plt.close()

def generate_subplot_reads(data, title, filename):
    """为每个tag创建子图显示读取量变化趋势"""
    colors = [
        "#F38BA8", "#EBA0AC", "#FAB387", "#F9E2AF",
        "#A6E3A1", "#94E2D5", "#89DCEB", "#74C7EC",
        "#89B4FA", "#B4BEFE", "#CBA6F7", "#F5C2E7",
        "#F5E0DC", "#F2CDCD", "#CDD6F4", "#BAC2DE",
    ]
    
    # 创建4x4的子图网格
    fig, axs = plt.subplots(4, 4, figsize=(20, 20))
    fig.suptitle(title, fontsize=16)
    
    # 找到所有tag中的最大值，用于统一y轴范围
    max_val = 0
    for tag_data in data:
        local_max = max(tag_data)  # 直接使用tag_data，因为它已经是数值列表
        max_val = max(max_val, local_max)
    
    for tag_idx in range(M):
        row = tag_idx // 4
        col = tag_idx % 4
        ax = axs[row, col]
        
        # 获取当前tag的数据
        periods = list(range(len(data[tag_idx])))
        values = data[tag_idx]
        
        # 绘制曲线
        ax.plot(periods, values, 
                color=colors[tag_idx % len(colors)],
                linewidth=1.5,
                marker='o',
                markersize=3)
        
        # 设置子图标题和标签
        ax.set_title(f'Tag {tag_idx + 1}')
        ax.set_xlabel('Period')
        ax.set_ylabel('Read Count')
        ax.grid(True, linestyle='--', alpha=0.6)
        
        # 统一y轴范围
        ax.set_ylim(0, max_val * 1.1)
    
    plt.tight_layout()
    plt.savefig(filename, bbox_inches="tight", dpi=300)
    plt.close()

if __name__ == "__main__":
    # 使用示例（文件路径需要根据实际情况修改）
    T, M, N, V, G, fre_del, fre_write, fre_read = read_input_file(
        "./test/sample_practice.in"
    )

    write_minus_del = [
        [w - d for w, d in zip(fre_write[i], fre_del[i])] for i in range(M)
    ]

    # 生成写入-删除差异图
    generate_plot(
        data=write_minus_del,
        ylabel="Write-Delete Difference",
        title="Storage Operation Difference Visualization",
        filename="write_delete_difference.png",
        cumulative_mode=True,
    )

    # 生成预读取数据图
    generate_plot(
        data=fre_read,
        ylabel="Pre-Read Value",
        title="Pre-Read Operation Visualization",
        filename="pre_read_visualization.png",
        cumulative_mode=False,  # 不累计
    )

    # 生成每个tag的读取量趋势子图
    generate_subplot_reads(
        data=fre_read,
        title="Read Operations by Tag and Period",
        filename="tag_read_trends.png"
    )
