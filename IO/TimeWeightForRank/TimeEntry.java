package IO.TimeWeightForRank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import IO.IO;

public class TimeEntry {
    /**
     * 入口点，输入一个tagSet和diskId，返回一个tagSet的排序
     * 
     * @param tagSet 要排序的标签集合
     * @param diskId 指定的磁盘ID
     * @return 排序后的标签列表
     */
    public static ArrayList<Integer> entrypoint(HashSet<Integer> tagSet, int diskId) {
        ArrayList<ArrayList<Integer>> readSizeByPeriod = IO.readSizeByPeriod;
        
        // 存储每个标签的时间得分（得分越高表示高峰期越早）
        HashMap<Integer, Double> temporalScores = new HashMap<>();
        
        // 获取总周期数
        int totalPeriods = readSizeByPeriod.size();
        
        // 举例：假设有5个周期
        // 第0期权重 = 5/5 = 1.0    （最早期权重最大）
        // 第1期权重 = 4/5 = 0.8
        // 第2期权重 = 3/5 = 0.6
        // 第3期权重 = 2/5 = 0.4
        // 第4期权重 = 1/5 = 0.2    （最晚期权重最小）
        
        // 计算每个标签的时间得分
        for (Integer tag : tagSet) {
            // 计算标签的总读取量
            long totalReads = 0;
            for (ArrayList<Integer> periodReads : readSizeByPeriod) {
                if (tag < periodReads.size()) {
                    totalReads += periodReads.get(tag);
                }
            }
            
            // 如果总读取量为0，跳过该标签
            if (totalReads == 0) {
                continue;
            }
            
            // 计算加权得分
            double weightedSum = 0.0;
            for (int period = 0; period < totalPeriods; period++) {
                // 计算时期权重（越早的时期权重越高）
                double periodWeight = (double)(totalPeriods - period) / totalPeriods;
                
                // 获取当前时期的读取量
                int readSize = 0;
                if (tag < readSizeByPeriod.get(period).size()) {
                    readSize = readSizeByPeriod.get(period).get(tag);
                }
                
                // 累加加权读取量
                weightedSum += periodWeight * readSize;
            }
            
            // 计算最终得分（归一化）
            temporalScores.put(tag, weightedSum / totalReads);
        }
        
        // 将标签按照时间得分降序排序
        // 排序结果：
        // - 返回列表的前面是高峰期较早的tag（高分）
        // - 返回列表的后面是高峰期较晚的tag（低分）
        ArrayList<Integer> sortedTags = new ArrayList<>(tagSet);
        Collections.sort(sortedTags, (a, b) -> {
            double scoreA = temporalScores.getOrDefault(a, 0.0);
            double scoreB = temporalScores.getOrDefault(b, 0.0);
            return Double.compare(scoreB, scoreA); // 降序排序，确保高分（早期高峰）的tag在前
        });
        
        return sortedTags;
    }
}
