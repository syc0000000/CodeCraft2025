import matplotlib.pyplot as plt
import numpy as np
from scipy.signal import find_peaks

def plot_disk_rw():
    # 读取数据
    try:
        with open('disk0RWEnd.txt', 'r') as file:
            rw_end_data = []
            head_pos_data = []
            for line in file:
                rw_end, head_pos = map(int, line.strip().split())
                rw_end_data.append(rw_end)
                head_pos_data.append(head_pos)
    except FileNotFoundError:
        print("Error: disk0RWEnd.txt file not found")
        return
    except ValueError:
        print("Error: Invalid data format in file")
        return

    # 创建时间点数组
    time_points = np.arange(1, len(rw_end_data) + 1)

    # 创建图形
    plt.figure(figsize=(12, 6))
    
    # 绘制两条曲线
    plt.plot(time_points, rw_end_data, 'b-', label='RWEnd Position')
    plt.plot(time_points, head_pos_data, 'r-', label='Head Position', alpha=0.7)
    
    # 每3600个点添加标记
    for i in range(0, len(rw_end_data), 3600):
        # RWEnd位置标记
        plt.plot(time_points[i], rw_end_data[i], 'bo')  # 蓝色圆点标记
        y_offset = -15 if rw_end_data[i] > 1000 else 15
        plt.annotate(str(rw_end_data[i]), 
                    xy=(time_points[i], rw_end_data[i]),
                    xytext=(0, y_offset), textcoords='offset points',
                    ha='center', va='bottom' if y_offset > 0 else 'top',
                    fontsize=8, color='blue')
        
        # 磁头位置标记
        plt.plot(time_points[i], head_pos_data[i], 'ro')  # 红色圆点标记
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
    
    # 检测并标记拐点
    rw_end_array = np.array(rw_end_data)
    # 计算一阶差分
    diff1 = np.diff(rw_end_array)
    # 计算二阶差分
    diff2 = np.diff(diff1)
    # 找到拐点（二阶差分的零点）
    inflection_points = np.where(np.diff(np.sign(diff2)))[0]
    
    # 标记拐点
    for i in inflection_points:
        if i < len(rw_end_data) and rw_end_data[i] >= 1000:  # 只标记值大于等于1000的拐点
            plt.plot(time_points[i], rw_end_data[i], 'g*', markersize=10)  # 绿色星形标记拐点
            y_offset = 15 if rw_end_data[i] > 1000 else -15
            plt.annotate(f'({time_points[i]}, {rw_end_data[i]})', 
                        xy=(time_points[i], rw_end_data[i]),
                        xytext=(10, y_offset), textcoords='offset points',
                        fontsize=8)
    
    # 设置图表属性
    plt.title('Disk 0 RWEnd and Head Position Over Time', fontsize=14)
    plt.xlabel('Time Slice', fontsize=12)
    plt.ylabel('Position', fontsize=12)
    plt.grid(True, linestyle='--', alpha=0.7)
    plt.legend(fontsize=10)
    
    # 优化布局
    plt.tight_layout()
    
    # 保存图表
    plt.savefig('disk_rw_plot.png', dpi=300, bbox_inches='tight')
    print("Chart has been saved as disk_rw_plot.png")
    
    # 显示图表
    plt.show()

if __name__ == "__main__":
    plot_disk_rw() 