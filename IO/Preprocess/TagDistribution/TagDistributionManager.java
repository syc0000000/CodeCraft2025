package IO.Preprocess.TagDistribution;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import IO.Preprocess.Preprocess;
import IO.Preprocess.hardcode.dist2;
import Info.Info;
import Info.model.LocalDisk;
import Info.model.Tag;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

/**
 * 负责处理标签与磁盘分配相关的功能
 */
public class TagDistributionManager {
    private static final ModuleLogger log = LoggerFactory.getLogger("TagDistribution");

    /**
     * 计算标签在磁盘上的分布
     * 
     * @param tagValues 标签值数组
     * @param isGA      是否使用遗传算法
     * @return 分配结果
     */
    public static Map<Integer, List<DiskDistributor.Split>> computeDistribution(int[] tagValues, boolean isGA) {
        Map<Integer, List<DiskDistributor.Split>> distribution = new HashMap<>();
        if (isGA) {
            distribution = DiskDistributor.computeDistributionByGA(tagValues);
        } else {
            distribution = DiskDistributor.createEvenDistribution(tagValues);
        }
        return distribution;
    }

    /**
     * 从文件加载分布结果
     * 
     * @param path 文件路径
     * @return 加载的分布结果
     */
    @SuppressWarnings("unchecked")
    public static Map<Integer, List<DiskDistributor.Split>> loadDistribution(String path) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            return (Map<Integer, List<DiskDistributor.Split>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error("加载失败: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }

    /**
     * 保存分布结果到文件
     * 
     * @param distribution 分布结果
     * @param path         保存路径
     */
    public static void saveDistribution(Map<Integer, List<DiskDistributor.Split>> distribution, String path) {
        File file = new File(path);
        try {
            file.getParentFile().mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(distribution);
                log.info("已保存到: " + path);
            }
        } catch (IOException e) {
            log.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 从文件加载标签排序结果
     * 
     * @param path 文件路径
     * @return 磁盘ID到排序后标签列表的映射
     */
    @SuppressWarnings("unchecked")
    public static Map<Integer, ArrayList<Integer>> loadSortedTags(String path) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            return (Map<Integer, ArrayList<Integer>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error("加载标签排序失败: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }

    /**
     * 保存标签排序结果到文件
     * 
     * @param sortedTags 磁盘ID到排序后标签列表的映射
     * @param path       保存路径
     */
    public static void saveSortedTags(Map<Integer, ArrayList<Integer>> sortedTags, String path) {
        File file = new File(path);
        try {
            file.getParentFile().mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(sortedTags);
                log.info("标签排序已保存到: " + path);
            }
        } catch (IOException e) {
            log.error("保存标签排序失败: " + e.getMessage());
        }
    }

    /**
     * 将排序后的标签结果应用到Tag中间位置
     * 
     * @param sortedTags 磁盘ID到排序后标签列表的映射
     */
    public static void applySortedTags(Map<Integer, ArrayList<Integer>> sortedTags) {
        for (Map.Entry<Integer, ArrayList<Integer>> entry : sortedTags.entrySet()) {
            int diskId = entry.getKey();
            ArrayList<Integer> sortedTagIds = entry.getValue();

            log.info("应用磁盘 " + diskId + " 的标签排序，共 " + sortedTagIds.size() + " 个标签");
            int tagTotalSize = 0;
            for (Integer tagId : sortedTagIds) {
                Tag tag = Info.tags.get(tagId);
                tagTotalSize += tag.lenthList.get(diskId);
            }

            LocalDisk disk = Info.localDiskTbl.get(diskId);
            double portion = (double) disk.logicalRWEnd / tagTotalSize;
            log.info("portion" + portion
                    + "; tagTotalSize" + tagTotalSize + " disk.logicalRWEnd" + disk.logicalRWEnd);
            // 记录排序结果到Tag的middle位置
            int currentPosition = 0;
            for (Integer tagId : sortedTagIds) {
                Tag tag = Info.tags.get(tagId);
                int tagSize = (int) (tag.lenthList.get(diskId) * portion);

                // 记录标签在磁盘上的中间位置
                tag.middleList.set(diskId, currentPosition + tagSize / 2);
                currentPosition += tagSize;
            }
        }
    }

    /**
     * 处理每个磁盘的标签排序（单线程版本）
     * 
     * @param diskToTag 每个磁盘的标签集合
     * @return 磁盘ID到排序后标签列表的映射
     */
    public static Map<Integer, ArrayList<Integer>> sortTagsForDisks(ArrayList<HashSet<Integer>> diskToTag) {
        Map<Integer, ArrayList<Integer>> sortedTags = new HashMap<>();

        log.info("开始对标签进行排序");
        long startTime = System.currentTimeMillis();

        // 依次处理每个磁盘
        for (int i = 0; i < Info.diskNum; i++) {
            if (diskToTag.get(i).isEmpty()) {
                continue;
            }

            long diskStartTime = System.currentTimeMillis();

            // 对当前磁盘的标签进行排序
            ArrayList<Integer> sortedTagIds = TimeWeightRanker.rankTags(diskToTag.get(i), i);

            long diskEndTime = System.currentTimeMillis();
            log.info("磁盘 " + i + " 标签排序完成，耗时: " + (diskEndTime - diskStartTime) + "ms");

            // 保存排序结果
            sortedTags.put(i, sortedTagIds);

            // 设置标签在磁盘上的位置
            int currentPosition = 0;
            for (Integer tagId : sortedTagIds) {
                Tag tag = Info.tags.get(tagId);
                int tagSize = tag.lenthList.get(i);

                // 记录标签在磁盘上的中间位置
                tag.middleList.set(i, currentPosition + tagSize / 2);
                currentPosition += tagSize;
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("所有磁盘的标签排序已完成，总耗时: " + (endTime - startTime) + "ms");

        return sortedTags;
    }

    /**
     * 初始化标签分布和排序
     */
    public static void initializeTagDistribution(String loadDistributionPath) {
        int[] tagValues = Preprocess.getTagsUnitUsage().stream().mapToInt(Integer::intValue).toArray();

        // 获取分布策略
        Map<Integer, List<DiskDistributor.Split>> distribution = dist2.createHardcodedDistribution();

        // // 如果指定了加载路径，从文件加载分布结果
        if (loadDistributionPath != null) {
            distribution = loadDistribution(loadDistributionPath);
        } else {
            // 计算并保存分布
            distribution = computeDistribution(tagValues, true);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String savePath = "distributions/distribution_" + timestamp + ".ser";
            saveDistribution(distribution, savePath);
        }

        // 每个磁盘有哪些tag
        ArrayList<HashSet<Integer>> diskToTag = new ArrayList<>();
        for (int i = 0; i < Info.diskNum; i++) {
            diskToTag.add(new HashSet<>());
        }

        // 创建Tag对象并设置分配关系
        for (int i = 0; i < tagValues.length; i++) {
            Tag tag = new Tag(i, tagValues[i], Info.diskNum);
            log.info("Tag " + i + " 的分配结果: " + distribution.get(i).toString());
            for (DiskDistributor.Split split : distribution.get(i)) {
                int diskId = split.diskIdx;
                int sizeInThisDisk = (int) Math.ceil(tagValues[i] * split.portion / 100.0);
                // lenthList的diskId位置，写入sizeInThisDisk
                tag.lenthList.set(diskId, sizeInThisDisk);
                diskToTag.get(diskId).add(i);
            }
            Info.tags.add(tag);
        }

        // 对每个磁盘的tag进行排序 (单线程)
        Map<Integer, ArrayList<Integer>> sortedTags = sortTagsForDisks(diskToTag);

        // 保存排序结果到文件
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String savePath = "tags/sortedTags_" + timestamp + ".ser";
        saveSortedTags(sortedTags, savePath);
        applySortedTags(sortedTags);
    }
}