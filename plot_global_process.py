import math

import matplotlib.pyplot as plt


def read_input_file(filename):
    """从文件读取输入数据的函数"""
    with open(filename, "r") as f:
        # 读取第一行参数
        header = f.readline().strip()
        T, M, N, V, G = map(int, header.split())

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


# 使用示例（文件路径需要根据实际情况修改）
T, M, N, V, G, fre_del, fre_write, fre_read = read_input_file(
    "./test/global_process_practice.txt"
)


def generate_plot(data, ylabel, title, filename):
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


# 生成写入-删除差异图
generate_plot(
    data=[[w - d for w, d in zip(fre_write[i], fre_del[i])] for i in range(M)],
    ylabel="Write-Delete Difference",
    title="Storage Operation Difference Visualization",
    filename="write_delete_difference.png",
)

# 生成预读取数据图
generate_plot(
    data=fre_read,
    ylabel="Pre-Read Value",
    title="Pre-Read Operation Visualization",
    filename="pre_read_visualization.png",
)
