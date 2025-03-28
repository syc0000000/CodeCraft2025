package IO.GAForRank;

import java.util.ArrayList;
import java.util.HashSet;

import Info.Info;

public class Entry {
    /**
     * 入口点，输入一个tagSet和diskId，返回一个tagSet的排序
     * 
     * @param tagSet 要排序的标签集合
     * @param diskId 指定的磁盘ID
     * @return 排序后的标签列表
     */
    public static ArrayList<Integer> entrypoint(HashSet<Integer> tagSet, int diskId) {
        // 边界情况处理
        if (tagSet == null || tagSet.isEmpty()) {
            return new ArrayList<>();
        }

        if (tagSet.size() == 1) {
            return new ArrayList<>(tagSet);
        }

        // 创建配置
        GAConfig config = new GAConfig(diskId);

        // 创建遗传算法实例
        GeneticAlgorithm ga = new GeneticAlgorithm(config);

        // 执行进化算法获取最优排序
        ArrayList<Integer> bestRank = ga.evolve(tagSet);

        return bestRank;
    }
}