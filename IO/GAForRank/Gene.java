package IO.GAForRank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import IO.IO;
import Info.Info;

// 基因
public class Gene {
    // tag排序
    private ArrayList<Integer> tagRank;
    // 每period的区间长度
    private ArrayList<Integer> periodLength;
    // 适应度
    private double fitness = Double.MAX_VALUE;
    // 标签位置缓存，用于加速indexOf操作
    private Map<Integer, Integer> tagPositionCache;

    // 基因的构造函数
    public Gene(ArrayList<Integer> tagRank, int diskId) {
        this.tagRank = tagRank;
        // 初始化标签位置缓存
        this.tagPositionCache = new HashMap<>();
        for (int i = 0; i < tagRank.size(); i++) {
            tagPositionCache.put(tagRank.get(i), i);
        }

        // 去IO读取每个period要读取的tag
        ArrayList<HashSet<Integer>> periodToTagSet = IO.periodToTagSet;
        // 计算每period的区间长度，每个period的区间长度为rank里面第一个middle到最后一个middle
        periodLength = new ArrayList<>();
        // 直接累计总适应度
        double totalFitness = 0;

        for (HashSet<Integer> tagSet : periodToTagSet) {
            // 针对这个period里面每一个tag，遍历tag，找到在rank里面最近到最远的tag之间的middle距离有多远
            int minPos = Integer.MAX_VALUE;
            int maxPos = Integer.MIN_VALUE;

            for (Integer tag : tagSet) {
                // 使用缓存查找位置，避免使用indexOf的O(n)复杂度
                Integer pos = tagPositionCache.get(tag);
                if (pos != null) {
                    minPos = Math.min(minPos, pos);
                    maxPos = Math.max(maxPos, pos);
                }
            }

            // 如果minPos和maxPos没有变化，说明这个period没有找到匹配的tag
            if (minPos == Integer.MAX_VALUE || maxPos == Integer.MIN_VALUE) {
                periodLength.add(0);
                continue;
            }

            // 现在知道minPos和maxPos，就可以计算这个period的区间长度，lenth=min到max之间所有tag的size相加
            int currentPeriodLength = 0;
            for (int i = minPos + 1; i < maxPos; i++) {
                // 再乘以总read请求数
                currentPeriodLength += Info.tags.get(tagRank.get(i)).lenthList.get(diskId)
                        * IO.readSizeByPeriod.get(i).get(tagRank.get(i));
            }
            currentPeriodLength += Info.tags.get(tagRank.get(minPos)).lenthList.get(diskId) / 2
                    * IO.readSizeByPeriod.get(minPos).get(tagRank.get(minPos));
            currentPeriodLength += Info.tags.get(tagRank.get(maxPos)).lenthList.get(diskId) / 2
                    * IO.readSizeByPeriod.get(maxPos).get(tagRank.get(maxPos));

            this.periodLength.add(currentPeriodLength);
            totalFitness += currentPeriodLength;
        }

        // 直接设置适应度，避免额外的遍历
        this.fitness = totalFitness;
    }

    // 计算适应度，整体区间长度越小，适应度越高
    public double calculateFitness() {
        // 如果已计算过适应度且periodLength未发生变化，直接返回
        if (fitness != Double.MAX_VALUE) {
            return fitness;
        }

        double totalFitness = 0;
        for (int i = 0; i < periodLength.size(); i++) {
            totalFitness += periodLength.get(i);
        }
        this.fitness = totalFitness;
        return fitness;
    }

    // 获取tagRank
    public ArrayList<Integer> getTagRank() {
        return tagRank;
    }

    // 获取标签在排序中的位置
    public int getTagPosition(int tag) {
        return tagPositionCache.getOrDefault(tag, -1);
    }

    // 设置特定位置的标签值，并更新缓存
    public void setTagAtPosition(int position, int tag) {
        int oldTag = tagRank.get(position);
        tagRank.set(position, tag);
        tagPositionCache.remove(oldTag);
        tagPositionCache.put(tag, position);
    }

    // 交换两个位置的标签，并更新缓存
    public void swapTags(int pos1, int pos2) {
        int tag1 = tagRank.get(pos1);
        int tag2 = tagRank.get(pos2);

        tagRank.set(pos1, tag2);
        tagRank.set(pos2, tag1);

        tagPositionCache.put(tag1, pos2);
        tagPositionCache.put(tag2, pos1);
    }

    // 获取适应度
    public double getFitness() {
        return fitness;
    }

    // 设置适应度
    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    // 深拷贝构造函数
    public Gene(Gene other) {
        this.tagRank = new ArrayList<>(other.tagRank);
        this.periodLength = new ArrayList<>(other.periodLength);
        this.fitness = other.fitness;
        this.tagPositionCache = new HashMap<>(other.tagPositionCache);
    }
}
