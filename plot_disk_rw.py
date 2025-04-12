import matplotlib.pyplot as plt
import numpy as np
from scipy.signal import find_peaks
import argparse

def read_disk_data(filename):
    try:
        with open(filename, 'r') as file:
            rw_end_data = []
            head_pos_data = []
            for line in file:
                rw_end, head_pos = map(int, line.strip().split())
                rw_end_data.append(rw_end)
                head_pos_data.append(head_pos)
        return rw_end_data, head_pos_data
    except FileNotFoundError:
        print(f"Error: {filename} file not found")
        return None, None
    except ValueError:
        print(f"Error: Invalid data format in {filename}")
        return None, None

def plot_disk_rw(disk_number, rw_end_data, head_pos_data, show_inflection_points=False):
    if rw_end_data is None or head_pos_data is None:
        return

    # 创建时间点数组
    time_points = np.arange(1, len(rw_end_data) + 1)

    # 创建图形，调整宽高比
    plt.figure(figsize=(24, 6))
    
    # 绘制两条曲线
    plt.plot(time_points, rw_end_data, 'b-', label='RWEnd Position')
    plt.plot(time_points, head_pos_data, 'r-', label='Head Position', alpha=0.7)
    
    # 每3600个点添加标记
    for i in range(0, len(rw_end_data), 3600):
        # RWEnd位置标记
        plt.plot(time_points[i], rw_end_data[i], 'bo')
        y_offset = -15 if rw_end_data[i] > 1000 else 15
        plt.annotate(str(rw_end_data[i]), 
                    xy=(time_points[i], rw_end_data[i]),
                    xytext=(0, y_offset), textcoords='offset points',
                    ha='center', va='bottom' if y_offset > 0 else 'top',
                    fontsize=8, color='blue')
        
        # 磁头位置标记
        plt.plot(time_points[i], head_pos_data[i], 'ro')
        y_offset = 15 if head_pos_data[i] > 1000 else -15
        plt.annotate(str(head_pos_data[i]), 
                    xy=(time_points[i], head_pos_data[i]),
                    xytext=(0, y_offset), textcoords='offset points',
                    ha='center', va='bottom' if y_offset > 0 else 'top',
                    fontsize=8, color='red')
        
        # 添加横轴标记
        plt.annotate(str(time_points[i]), 
                    xy=(time_points[i], plt.ylim()[0]),
                    xytext=(0, -5), textcoords='offset points',
                    ha='center', va='top',
                    fontsize=8)
    
    if show_inflection_points:
        rw_end_array = np.array(rw_end_data)
        diff1 = np.diff(rw_end_array)
        diff2 = np.diff(diff1)
        inflection_points = np.where(np.diff(np.sign(diff2)))[0]
        
        for i in inflection_points:
            if i < len(rw_end_data) and rw_end_data[i] >= 1000:
                plt.plot(time_points[i], rw_end_data[i], 'g*', markersize=10)
                y_offset = 15 if rw_end_data[i] > 1000 else -15
                plt.annotate(f'({time_points[i]}, {rw_end_data[i]})', 
                            xy=(time_points[i], rw_end_data[i]),
                            xytext=(10, y_offset), textcoords='offset points',
                            fontsize=8)
    
    # 设置图表属性
    plt.title(f'Disk {disk_number} RWEnd and Head Position Over Time', fontsize=14)
    plt.xlabel('Time Slice', fontsize=12)
    plt.ylabel('Position', fontsize=12)
    plt.grid(True, linestyle='--', alpha=0.7)
    plt.legend(fontsize=10)
    
    # 优化布局
    plt.tight_layout()
    
    # 保存高精度图表
    plt.savefig(f'disk{disk_number}_rw_plot.png', dpi=600, bbox_inches='tight')
    print(f"Chart for Disk {disk_number} has been saved as disk{disk_number}_rw_plot.png")
    
    # 关闭图形以释放内存
    plt.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Plot disk RWEnd and head position')
    parser.add_argument('--show-inflection', action='store_true', 
                      help='Show inflection points on the plot')
    
    args = parser.parse_args()
    
    # 处理disk0数据
    disk0_rw, disk0_head = read_disk_data('disk0RWEnd.txt')
    plot_disk_rw(0, disk0_rw, disk0_head, show_inflection_points=args.show_inflection)
    
    # 处理disk5数据
    disk5_rw, disk5_head = read_disk_data('disk5RWEnd.txt')
    plot_disk_rw(5, disk5_rw, disk5_head, show_inflection_points=args.show_inflection)