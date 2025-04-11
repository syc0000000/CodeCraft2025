package MultiReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import IO.model.CompleteCommandOut;
import IO.model.MultiReadCommandOut;
import Info.Info;
import Info.model.LocalDisk;
import Info.model.TagMeta;
import Info.model.Action;
import Info.model.UserObject;
import Info.model.ReadTask;
import Logger.LoggerFactory;
import Logger.LoggerFactory.ModuleLogger;

public class RangeReader implements MultiReaderStrategy {
    ModuleLogger log = LoggerFactory.getLogger("Reader");
    // period-diskId-ptrId-一个List的range
    public ArrayList<ArrayList<ArrayList<ArrayList<Range>>>> rangeList = new ArrayList<>();
    // period-diskId-ptrId-任意一个unit会不会在range中
    private ArrayList<ArrayList<ArrayList<ArrayList<Boolean>>>> isInRange = new ArrayList<>();

    // 增加range分配策略常量
    public static final int POSITION_BALANCED_STRATEGY = 0;
    public static final int READ_SIZE_BALANCED_STRATEGY = 1;
    public static final int SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY = 2;
    public static final int SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY = 3;
    public static final int ACTIVE_UNITS_BALANCED_STRATEGY = 4;

    private static HashMap<Integer, Integer> periodToStrategy = new HashMap<>();
    private static int defaultStrategy = ACTIVE_UNITS_BALANCED_STRATEGY;
    static {
        // periodToStrategy.put(6, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(7, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(8, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(9, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(10, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(17, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(18, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(19, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(20, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(21, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(22, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(23, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(27, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(28, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(34, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(35, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(36, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(37, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(38, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(39, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(40, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(41, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(42, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(43, SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(44, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(45, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(46, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);
        // periodToStrategy.put(47, SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY);

    }

    public static class Range {
        int start;
        int end;
        int diskId;

        public Range(int start, int end, int diskId) {
            this.start = start;
            this.end = end;
            this.diskId = diskId;
        }

        public boolean isInRange(int ptr) {
            return ptr >= start && ptr <= end;
        }

        @Override
        public String toString() {
            return "Range{" +
                    "start=" + start +
                    ", end=" + end +
                    ", diskId=" + diskId +
                    '}';
        }

    }

    public RangeReader(ArrayList<HashSet<Integer>> periodToTagSet) {
        this(periodToTagSet, defaultStrategy);
    }

    public RangeReader(ArrayList<HashSet<Integer>> periodToTagSet, int strategy) {
        // 入参：periodToTagSet，表示每个period可接受的tag集合
        // 1. 根据tag集合，依次找出每个磁盘每个period的总range
        // 2. 根据总range，分出两个磁头的range
        // 3. 根据range，分出两个磁头的isInRange
        for (int period = 0; period < periodToTagSet.size(); period++) {
            rangeList.add(new ArrayList<>());
            isInRange.add(new ArrayList<>());
            HashSet<Integer> tagSet = periodToTagSet.get(period);
            for (int diskId = 0; diskId < Info.diskNum; diskId++) {
                rangeList.get(period).add(new ArrayList<>());
                isInRange.get(period).add(new ArrayList<>());
                LocalDisk disk = Info.localDiskTbl.get(diskId);
                // 查TagSet，拿到对应Tag在磁盘中的位置
                ArrayList<Range> ranges = new ArrayList<>();
                for (int tag : tagSet) {
                    TagMeta tagMeta = disk.getTagMetaByTagId(tag);
                    if (tagMeta != null) {
                        int start = tagMeta.left;
                        int end = tagMeta.right;
                        ranges.add(new Range(start, end, diskId));
                    }
                    // rangeList.get(period).get(diskId).get(ptrId).add(new Range(start, end,
                    // diskId));
                }
                // 排序(从小到大)
                ranges.sort((a, b) -> a.start - b.start);

                // 根据选择的策略分配range
                ArrayList<Range> leftRanges = new ArrayList<>();
                ArrayList<Range> rightRanges = new ArrayList<>();

                if (strategy == POSITION_BALANCED_STRATEGY) {
                    // 使用基于位置的分配策略
                    distributeRangesByPosition(ranges, leftRanges, rightRanges, disk);
                } else if (strategy == READ_SIZE_BALANCED_STRATEGY) {
                    // 使用基于读取量的分配策略
                    distributeRangesByReadSize(ranges, leftRanges, rightRanges, disk, period);
                } else if (strategy == SEQUENTIAL_READ_SIZE_BALANCED_STRATEGY) {
                    // 使用顺序不交叉且读取量平衡的策略
                    distributeRangesBySequentialReadSize(ranges, leftRanges, rightRanges, disk, period);
                } else if (strategy == SPLIT_TAG_READ_SIZE_BALANCED_STRATEGY) {
                    // 使用允许切分Tag的读取量平衡策略
                    distributeRangesBySplitTagReadSize(ranges, leftRanges, rightRanges, disk, period);
                } else if (strategy == ACTIVE_UNITS_BALANCED_STRATEGY) {
                    // 使用有任务unit数量平衡的策略
                    distributeRangesByActiveUnits(ranges, leftRanges, rightRanges, disk, period, 1.1);
                }
                if (periodToStrategy.containsKey(period)) {
                    strategy = periodToStrategy.get(period);
                }

                // 两个磁头的range
                rangeList.get(period).get(diskId).add(leftRanges);
                rangeList.get(period).get(diskId).add(rightRanges);
                isInRange.get(period).get(diskId).add(new ArrayList<>());
                isInRange.get(period).get(diskId).add(new ArrayList<>());
                // 两个磁头的isInRange
                for (int i = 0; i <= disk.logicalRWEnd; i++) {
                    boolean isInLeftRange = false;
                    boolean isInRightRange = false;
                    for (Range range : leftRanges) {
                        if (range.isInRange(i)) {
                            isInLeftRange = true;
                        }
                    }
                    for (Range range : rightRanges) {
                        if (range.isInRange(i)) {
                            isInRightRange = true;
                        }
                    }
                    // 两个磁头的isInRange
                    isInRange.get(period).get(diskId).get(0).add(isInLeftRange);
                    isInRange.get(period).get(diskId).get(1).add(isInRightRange);
                }
            }
        }
        log.info("RangeReader初始化完成,rangeList: " + Arrays.deepToString(rangeList.toArray()));
    }

    // 允许切分Tag的读取量平衡策略
    private void distributeRangesBySplitTagReadSize(ArrayList<Range> ranges, ArrayList<Range> leftRanges,
            ArrayList<Range> rightRanges, LocalDisk disk, int period) {
        if (ranges.isEmpty()) {
            return;
        }

        // 计算每个range的readSize和总readSize
        ArrayList<Integer> rangeReadSizes = new ArrayList<>();
        int totalReadSize = 0;

        ArrayList<Integer> tagIds = new ArrayList<>(); // 保存每个range对应的tagId

        for (Range range : ranges) {
            int tagId = -1;
            // 查找range对应的tagId
            for (TagMeta tagMeta : disk.tagMetas) {
                if (tagMeta.left == range.start && tagMeta.right == range.end) {
                    tagId = tagMeta.tagId;
                    break;
                }
            }

            tagIds.add(tagId);

            // 获取这个tag在当前period的readSize
            int readSize = 0;
            if (tagId != -1) {
                if (period < Info.tags.get(tagId).readSizeByPeriod.size()) {
                    readSize = Info.tags.get(tagId).readSizeByPeriod.get(period);
                }
            }

            rangeReadSizes.add(readSize);
            totalReadSize += readSize;
        }

        // 理想情况下每边应该有的读取量
        int targetReadSize = totalReadSize / 2;

        // 先尝试找到最接近目标读取量的完整range分割方案
        int bestSplitIndex = 0;
        int currentSum = 0;
        int minDiff = Integer.MAX_VALUE;

        for (int i = 0; i < ranges.size(); i++) {
            currentSum += rangeReadSizes.get(i);
            int diff = Math.abs(currentSum - targetReadSize);

            if (diff < minDiff) {
                minDiff = diff;
                bestSplitIndex = i + 1; // 在i之后分割
            }
        }

        // 计算使用完整range分割的左右读取量
        int leftSum = 0;
        for (int i = 0; i < bestSplitIndex; i++) {
            leftSum += rangeReadSizes.get(i);
        }

        int rightSum = totalReadSize - leftSum;
        int imbalance = Math.abs(leftSum - rightSum);

        // 如果不平衡度太大，考虑切分一个range来改善平衡性
        if (bestSplitIndex > 0 && bestSplitIndex < ranges.size() && imbalance > 0) {
            // 确定是切分bestSplitIndex前的range还是后的range
            boolean splitPrevious = false;
            int rangeToSplitIndex;

            if (leftSum > rightSum) {
                // 左侧读取量过大，考虑切分最后一个左侧range
                rangeToSplitIndex = bestSplitIndex - 1;
                splitPrevious = true;
            } else {
                // 右侧读取量过大，考虑切分第一个右侧range
                rangeToSplitIndex = bestSplitIndex;
                splitPrevious = false;
            }

            // 获取要切分的range信息
            Range rangeToSplit = ranges.get(rangeToSplitIndex);
            int rangeReadSize = rangeReadSizes.get(rangeToSplitIndex);

            // 只有在range足够大时才考虑切分
            if (rangeReadSize > 10 && rangeToSplit.end - rangeToSplit.start > 5) {
                // 计算需要转移的读取量
                int transferAmount = imbalance / 2;

                // 如果transferAmount太小，不值得切分
                if (transferAmount > 5) {
                    // 计算切分比例
                    double splitRatio = (double) transferAmount / rangeReadSize;

                    // 根据比例计算切分点
                    int splitPoint;
                    if (splitPrevious) {
                        // 从左侧range末尾切出一部分到右侧
                        splitPoint = rangeToSplit.start
                                + (int) ((rangeToSplit.end - rangeToSplit.start) * (1 - splitRatio));
                    } else {
                        // 从右侧range开头切出一部分到左侧
                        splitPoint = rangeToSplit.start
                                + (int) ((rangeToSplit.end - rangeToSplit.start) * splitRatio);
                    }

                    // 确保切分点有效
                    splitPoint = Math.max(rangeToSplit.start + 1, Math.min(rangeToSplit.end - 1, splitPoint));

                    // 创建两个新的range
                    Range leftPart = new Range(rangeToSplit.start, splitPoint, rangeToSplit.diskId);
                    Range rightPart = new Range(splitPoint + 1, rangeToSplit.end, rangeToSplit.diskId);

                    // 计算切分后的读取量分配
                    double leftPartRatio = (double) (splitPoint - rangeToSplit.start + 1)
                            / (rangeToSplit.end - rangeToSplit.start + 1);
                    double rightPartRatio = 1.0 - leftPartRatio;

                    int leftPartReadSize = (int) (rangeReadSize * leftPartRatio);
                    int rightPartReadSize = rangeReadSize - leftPartReadSize;

                    // 把切分的范围分配给左右两侧
                    for (int i = 0; i < ranges.size(); i++) {
                        if (i < rangeToSplitIndex) {
                            leftRanges.add(ranges.get(i));
                        } else if (i > rangeToSplitIndex) {
                            rightRanges.add(ranges.get(i));
                        } else {
                            // 这是被切分的range
                            if (splitPrevious) {
                                leftRanges.add(leftPart);
                                rightRanges.add(rightPart);
                            } else {
                                leftRanges.add(leftPart);
                                rightRanges.add(rightPart);
                            }
                        }
                    }

                    // 记录切分后的分配结果
                    int newLeftSum = leftSum;
                    int newRightSum = rightSum;

                    if (splitPrevious) {
                        newLeftSum -= rightPartReadSize;
                        newRightSum += rightPartReadSize;
                    } else {
                        newLeftSum += leftPartReadSize;
                        newRightSum -= leftPartReadSize;
                    }

                    log.debug("允许切分Tag的读取量均衡分配 - 磁盘" + disk.diskId + ", 周期" + period +
                            ", 切分前: 左侧读取量:" + leftSum + ", 右侧读取量:" + rightSum +
                            ", 切分后: 左侧读取量:" + newLeftSum + ", 右侧读取量:" + newRightSum +
                            ", 切分点:" + splitPoint + ", 切分的Tag:" +
                            (tagIds.get(rangeToSplitIndex) == -1 ? "未知" : tagIds.get(rangeToSplitIndex)));

                    // 已完成分配，直接返回
                    return;
                }
            }
        }

        // 如果不需要切分或不适合切分，就使用完整range分割
        for (int i = 0; i < ranges.size(); i++) {
            if (i < bestSplitIndex) {
                leftRanges.add(ranges.get(i));
            } else {
                rightRanges.add(ranges.get(i));
            }
        }

        log.debug("完整range分割的读取量均衡分配 - 磁盘" + disk.diskId + ", 周期" + period +
                ", 左侧读取量:" + leftSum + ", 右侧读取量:" + rightSum);
    }

    // 基于位置的分配策略
    private void distributeRangesByPosition(ArrayList<Range> ranges, ArrayList<Range> leftRanges,
            ArrayList<Range> rightRanges, LocalDisk disk) {
        // 遍历所有start和end，从最接近中心的点划断（不管是start还是end）
        int mid = disk.logicalRWEnd / 2;
        int minDiff = Integer.MAX_VALUE;
        int splitPoint = 0;
        for (Range range : ranges) {
            int diff = Math.abs(range.start - mid);
            if (diff < minDiff) {
                minDiff = diff;
                splitPoint = range.start;
            }
        }
        // 分出两个磁头的range
        for (Range range : ranges) {
            if (range.start < splitPoint) {
                leftRanges.add(range);
            } else {
                rightRanges.add(range);
            }
        }
    }

    // 基于读取量的分配策略
    private void distributeRangesByReadSize(ArrayList<Range> ranges, ArrayList<Range> leftRanges,
            ArrayList<Range> rightRanges, LocalDisk disk, int period) {
        // 计算所有Range的总readSize
        int totalReadSize = 0;
        ArrayList<Integer> rangeReadSizes = new ArrayList<>();

        for (Range range : ranges) {
            int tagId = -1;
            // 查找range对应的tagId
            for (TagMeta tagMeta : disk.tagMetas) {
                if (tagMeta.left == range.start && tagMeta.right == range.end) {
                    tagId = tagMeta.tagId;
                    break;
                }
            }

            // 获取这个tag在当前period的readSize
            int readSize = 0;
            if (tagId != -1) {
                if (period < Info.tags.get(tagId).readSizeByPeriod.size()) {
                    readSize = Info.tags.get(tagId).readSizeByPeriod.get(period);
                }
            }

            totalReadSize += readSize;
            rangeReadSizes.add(readSize);
        }

        // 使用贪心算法分配range，尽量使两边readSize平衡
        int leftReadSize = 0;
        int rightReadSize = 0;

        // 按照readSize从大到小排序ranges
        ArrayList<Range> sortedRanges = new ArrayList<>(ranges);
        for (int i = 0; i < sortedRanges.size(); i++) {
            for (int j = i + 1; j < sortedRanges.size(); j++) {
                int readSizeI = rangeReadSizes.get(ranges.indexOf(sortedRanges.get(i)));
                int readSizeJ = rangeReadSizes.get(ranges.indexOf(sortedRanges.get(j)));
                if (readSizeI < readSizeJ) {
                    Range temp = sortedRanges.get(i);
                    sortedRanges.set(i, sortedRanges.get(j));
                    sortedRanges.set(j, temp);
                }
            }
        }

        // 从大到小分配ranges，选择当前读取量较小的一侧
        for (Range range : sortedRanges) {
            int readSize = rangeReadSizes.get(ranges.indexOf(range));
            if (leftReadSize <= rightReadSize) {
                leftRanges.add(range);
                leftReadSize += readSize;
            } else {
                rightRanges.add(range);
                rightReadSize += readSize;
            }
        }
    }

    // 顺序不交叉且读取量平衡的策略
    private void distributeRangesBySequentialReadSize(ArrayList<Range> ranges, ArrayList<Range> leftRanges,
            ArrayList<Range> rightRanges, LocalDisk disk, int period) {
        if (ranges.isEmpty()) {
            return;
        }

        // 首先计算每个range的readSize
        ArrayList<Integer> rangeReadSizes = new ArrayList<>();
        for (Range range : ranges) {
            int tagId = -1;
            // 查找range对应的tagId
            for (TagMeta tagMeta : disk.tagMetas) {
                if (tagMeta.left == range.start && tagMeta.right == range.end) {
                    tagId = tagMeta.tagId;
                    break;
                }
            }

            // 获取这个tag在当前period的readSize
            int readSize = 0;
            if (tagId != -1) {
                if (period < Info.tags.get(tagId).readSizeByPeriod.size()) {
                    readSize = Info.tags.get(tagId).readSizeByPeriod.get(period);
                }
            }

            rangeReadSizes.add(readSize);
        }

        // 尝试所有可能的划分点，找到使左右读取量差异最小的那个点
        int minDiff = Integer.MAX_VALUE;
        int bestSplitIndex = 0; // 默认在第一个range后分割

        for (int splitIndex = 0; splitIndex < ranges.size(); splitIndex++) {
            // 计算左侧总读取量
            int leftSum = 0;
            for (int i = 0; i < splitIndex; i++) {
                leftSum += rangeReadSizes.get(i);
            }

            // 计算右侧总读取量
            int rightSum = 0;
            for (int i = splitIndex; i < ranges.size(); i++) {
                rightSum += rangeReadSizes.get(i);
            }

            // 计算差异并更新最佳分割点
            int diff = Math.abs(leftSum - rightSum);
            if (diff < minDiff) {
                minDiff = diff;
                bestSplitIndex = splitIndex;
            }
        }

        // 根据最佳分割点分配ranges
        for (int i = 0; i < ranges.size(); i++) {
            if (i < bestSplitIndex) {
                leftRanges.add(ranges.get(i));
            } else {
                rightRanges.add(ranges.get(i));
            }
        }

        // 记录分配结果到日志
        int leftSum = 0;
        for (int i = 0; i < bestSplitIndex; i++) {
            leftSum += rangeReadSizes.get(i);
        }

        int rightSum = 0;
        for (int i = bestSplitIndex; i < ranges.size(); i++) {
            rightSum += rangeReadSizes.get(i);
        }

        log.debug("顺序不交叉的读取量均衡分配 - 磁盘" + disk.diskId + ", 周期" + period +
                ", 左侧读取量:" + leftSum + ", 右侧读取量:" + rightSum +
                ", 分割点:" + (ranges.isEmpty() ? "无" : ranges.get(bestSplitIndex).start));
    }

    // 基于有任务unit数量平衡的策略
    private void distributeRangesByActiveUnits(ArrayList<Range> ranges, ArrayList<Range> leftRanges,
            ArrayList<Range> rightRanges, LocalDisk disk, int period,
            double readRatioThreshold) {
        if (ranges.isEmpty()) {
            return;
        }

        // 计算每个range的物理单元数、读取量和估算的有任务unit数量
        ArrayList<Integer> rangeUnits = new ArrayList<>(); // 每个range的单元数
        ArrayList<Integer> rangeReadSizes = new ArrayList<>(); // 每个range的读取量
        ArrayList<Integer> rangeActiveUnits = new ArrayList<>(); // 每个range的估算有任务unit数量
        ArrayList<Integer> tagIds = new ArrayList<>(); // 每个range对应的tagId

        int totalUnits = 0;
        int totalReadSize = 0;
        int totalActiveUnits = 0;

        for (Range range : ranges) {
            int rangeSize = range.end - range.start + 1; // 范围内的单元数
            rangeUnits.add(rangeSize);
            totalUnits += rangeSize;

            int tagId = -1;
            // 查找range对应的tagId
            for (TagMeta tagMeta : disk.tagMetas) {
                if (tagMeta.left == range.start && tagMeta.right == range.end) {
                    tagId = tagMeta.tagId;
                    break;
                }
            }

            tagIds.add(tagId);

            // 获取这个tag在当前period的readSize
            int readSize = 0;
            if (tagId != -1) {
                if (period < Info.tags.get(tagId).readSizeByPeriod.size()) {
                    readSize = Info.tags.get(tagId).readSizeByPeriod.get(period);
                }
            }

            rangeReadSizes.add(readSize);
            totalReadSize += readSize;

            // 估算有任务unit数量
            int activeUnits;
            if (readSize >= rangeSize * readRatioThreshold) {
                // 如果总读取量超过区域大小的readRatioThreshold倍，认为所有unit都有任务
                activeUnits = rangeSize;
            } else {
                // 否则估算有任务unit数量为总读取量/readRatioThreshold（取整）
                activeUnits = (int) Math.ceil(readSize / readRatioThreshold);
            }

            rangeActiveUnits.add(activeUnits);
            totalActiveUnits += activeUnits;
        }

        // 目标每侧有任务unit数量
        int targetActiveUnits = totalActiveUnits / 2;

        // 尝试所有可能的划分点，找到使左右两边有任务unit数量最接近的那个点
        int bestSplitIndex = 0;
        int minDiff = Integer.MAX_VALUE;

        for (int splitIndex = 1; splitIndex <= ranges.size(); splitIndex++) {
            // 计算左侧有任务unit数量
            int leftActiveUnits = 0;
            for (int i = 0; i < splitIndex; i++) {
                leftActiveUnits += rangeActiveUnits.get(i);
            }

            // 计算右侧有任务unit数量
            int rightActiveUnits = totalActiveUnits - leftActiveUnits;

            // 计算差异并更新最佳分割点
            int diff = Math.abs(leftActiveUnits - rightActiveUnits);
            if (diff < minDiff) {
                minDiff = diff;
                bestSplitIndex = splitIndex;
            }
        }

        // 根据最佳分割点分配ranges
        for (int i = 0; i < ranges.size(); i++) {
            if (i < bestSplitIndex) {
                leftRanges.add(ranges.get(i));
            } else {
                rightRanges.add(ranges.get(i));
            }
        }

        // 计算最终分配结果的指标
        int leftUnits = 0;
        int leftReadSize = 0;
        int leftActiveUnits = 0;
        for (int i = 0; i < bestSplitIndex; i++) {
            leftUnits += rangeUnits.get(i);
            leftReadSize += rangeReadSizes.get(i);
            leftActiveUnits += rangeActiveUnits.get(i);
        }

        int rightUnits = totalUnits - leftUnits;
        int rightReadSize = totalReadSize - leftReadSize;
        int rightActiveUnits = totalActiveUnits - leftActiveUnits;

        log.debug("有任务unit数量平衡分配 - 磁盘" + disk.diskId + ", 周期" + period +
                ", 左侧: 单元数=" + leftUnits + ", 读取量=" + leftReadSize + ", 估算有任务unit数=" + leftActiveUnits +
                ", 右侧: 单元数=" + rightUnits + ", 读取量=" + rightReadSize + ", 估算有任务unit数=" + rightActiveUnits +
                ", 总有任务unit数=" + totalActiveUnits + ", 阈值=" + readRatioThreshold);
    }

    // 计算这一tick的目的地，考虑任务、range、磁头位置
    public int calculateTarget(int index, LocalDisk disk) {
        int period = Info.timestamp / 1800;
        if (period >= rangeList.size()) {
            period = rangeList.size() - 1;
        }
        int diskId = disk.diskId;
        // 向后扫任务
        int closestPosition = -1;

        for (int pos = disk.ptr[index]; pos <= disk.logicalRWEnd; pos++) {
            if (disk.unitData.get(pos).isInTask && isInRange.get(period).get(diskId).get(index).get(pos)) {
                closestPosition = pos;
                break;
            }
        }
        if (closestPosition != -1) {
            return closestPosition;
        }

        for (int pos = 0; pos < disk.ptr[index]; pos++) {
            if (disk.unitData.get(pos).isInTask && isInRange.get(period).get(diskId).get(index).get(pos)) {
                closestPosition = pos;
                break;
            }
        }

        return closestPosition;
    }

    @Override
    public void read(int diskId, MultiReadCommandOut readCommandOut,
            HashSet<CompleteCommandOut> completeCommandOuts) {
        LocalDisk disk = Info.localDiskTbl.get(diskId);
        int tokenleft[] = new int[2];
        tokenleft[0] = Info.tokenPerTick;
        tokenleft[1] = Info.tokenPerTick;
        for (int index = 0; index < 2; index++) {
            boolean hasPassOrRead = false;
            while (tokenleft[index] > 0) {
                boolean isInTask = disk.unitData.get(disk.ptr[index]).isInTask;
                if (isInTask) {
                    if (tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                        int objId = disk.unitData.get(disk.ptr[index]).objId;
                        int blockId = disk.unitData.get(disk.ptr[index]).blockId;
                        UserObject object = Info.objMap.get(objId);
                        // 有任务就直接处理
                        Iterator<ReadTask> iterator = object.readTasks.iterator();
                        while (iterator.hasNext()) {
                            ReadTask readTask = iterator.next();

                            if (readTask.blockNotFinished.contains(blockId)) {

                                log.debug("硬盘" + diskId + "任务ID" + readTask.taskId + "块ID" + blockId + "磁头"
                                        + index + "读到块" + disk.ptr[index]);
                                // 处理块
                                readTask.blockNotFinished.remove(blockId);
                                readTask.blockFinished.add(blockId);
                                log.debug("任务是否完成" + readTask.blockNotFinished.isEmpty());
                                if (readTask.blockNotFinished.isEmpty()) {
                                    log.debug("上报任务id" + readTask.taskId);
                                    completeCommandOuts.add(new CompleteCommandOut(readTask.taskId));
                                    // 移除这个任务
                                    Info.readTasksInRecent105Tick.get(Info.readTasksInRecent105Tick.size() - 1
                                            - (Info.timestamp - readTask.startTime))
                                            .remove(readTask);
                                    iterator.remove();
                                }
                            }
                        }
                        // 设为没有任务
                        disk.unitData.get(disk.ptr[index]).isInTask = false;
                        // 输出
                        readCommandOut.actions.get(index).add(Action.READ);
                        disk.ptrDoAction(index, Action.READ);
                        tokenleft[index] -= disk.pretoken[index];
                        hasPassOrRead = true;
                        continue;
                    } else {
                        // 如果token不够，则直接退出
                        break;
                    }
                }

                // 如果没有任务，则直接向后寻找
                int target = calculateTarget(index, disk);
                int k = target - disk.ptr[index];
                log.debug("k " + k + " tokenleft " + tokenleft[index] + " disk.ptr[index] "
                        + disk.ptr[index] + " closestTaskPosition " + target + "preoper "
                        + disk.preoper[index] + " pretoken " + disk.pretoken[index]);
                if (tokenleft[index] == 0) {
                    // 解决coner case k>0 preoper是read，但tokenleft[index]==0
                    break;
                }
                // if (k == 1) {
                // if (disk.preoper[index] == Action.READ && disk.pretoken[index] < 52
                // && tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                // readCommandOut.actions.get(index).add(Action.READ);
                // disk.ptrDoAction(index, Action.READ);
                // tokenleft[index] -= disk.pretoken[index];
                // hasPassOrRead = true;
                // } else if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                // readCommandOut.actions.get(index).add(Action.PASS);
                // disk.ptrDoAction(index, Action.PASS);
                // tokenleft[index] -= disk.pretoken[index];
                // hasPassOrRead = true;
                // }
                // } else if (k == 2) {
                // if (disk.preoper[index] == Action.READ && disk.pretoken[index] < 34
                // && tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                // readCommandOut.actions.get(index).add(Action.READ);
                // disk.ptrDoAction(index, Action.READ);
                // tokenleft[index] -= disk.pretoken[index];
                // hasPassOrRead = true;
                // if (tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                // readCommandOut.actions.get(index).add(Action.READ);
                // disk.ptrDoAction(index, Action.READ);
                // tokenleft[index] -= disk.pretoken[index];
                // }
                // } else if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                // readCommandOut.actions.get(index).add(Action.PASS);
                // disk.ptrDoAction(index, Action.PASS);
                // tokenleft[index] -= disk.pretoken[index];
                // hasPassOrRead = true;
                // if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                // readCommandOut.actions.get(index).add(Action.PASS);
                // disk.ptrDoAction(index, Action.PASS);
                // tokenleft[index] -= disk.pretoken[index];
                // }
                // }
                // } else if (k == 3) {
                // if (disk.preoper[index] == Action.READ && disk.pretoken[index] < 28
                // && tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                // readCommandOut.actions.get(index).add(Action.READ);
                // disk.ptrDoAction(index, Action.READ);
                // tokenleft[index] -= disk.pretoken[index];
                // hasPassOrRead = true;
                // if (tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                // readCommandOut.actions.get(index).add(Action.READ);
                // disk.ptrDoAction(index, Action.READ);
                // tokenleft[index] -= disk.pretoken[index];
                // }
                // if (tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                // readCommandOut.actions.get(index).add(Action.READ);
                // disk.ptrDoAction(index, Action.READ);
                // tokenleft[index] -= disk.pretoken[index];
                // }
                // } else if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                // readCommandOut.actions.get(index).add(Action.PASS);
                // disk.ptrDoAction(index, Action.PASS);
                // tokenleft[index] -= disk.pretoken[index];
                // hasPassOrRead = true;
                // if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                // readCommandOut.actions.get(index).add(Action.PASS);
                // disk.ptrDoAction(index, Action.PASS);
                // tokenleft[index] -= disk.pretoken[index];
                // }
                // if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                // readCommandOut.actions.get(index).add(Action.PASS);
                // disk.ptrDoAction(index, Action.PASS);
                // tokenleft[index] -= disk.pretoken[index];
                // }
                // }
                // }
                // pro哥同款方案，距离>9pass,距离<9read
                if (k <= 9 && k > 0) {
                    while (k > 0 && tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                        readCommandOut.actions.get(index).add(Action.READ);
                        disk.ptrDoAction(index, Action.READ);
                        tokenleft[index] -= disk.pretoken[index];
                        hasPassOrRead = true;
                        k--;
                    }
                    if (tokenleft[index] - disk.calculateToken(index, Action.READ) < 0) {
                        break;
                    }
                }
                // 任务离得很远
                else {
                    log.debug("向后寻找不到任务");
                    if (target != -1 && !hasPassOrRead && (k > Info.tokenPerTick || k < 0)) {
                        log.debug("距离 > G，执行跳转到" + target);
                        readCommandOut.actions.get(index).add(Action.JUMP);
                        readCommandOut.jumpTargets.set(index, target);
                        disk.ptrDoAction(index, Action.JUMP, target);
                        tokenleft[index] -= Info.tokenPerTick;
                        break;
                    } else if (k < 0) {
                        // 这里实际上是两种情况，一种是整个盘就没有任务，k=-1-disk.ptr[index]
                        // 一种是后面的任务已经被消化完，盘上有任务，但在ptr前面，但是又因为已经有过其他操作，所以不能跳转
                        break;
                    } else if (k == 0) {
                        // k=0情况，表示目标位置就是当前位置，无需操作
                        break;
                    } else {
                        // 此时，说明盘上有任务
                        log.debug("盘上有任务，执行pass " + k + " tokenleft " + tokenleft[index]);
                        while (k > 0 && tokenleft[index] > 0) {
                            readCommandOut.actions.get(index).add(Action.PASS);
                            disk.ptrDoAction(index, Action.PASS);
                            tokenleft[index] -= disk.pretoken[index];
                            hasPassOrRead = true;
                            k--;
                        }
                        if (tokenleft[index] == 0) {
                            break;
                        }
                    }
                }
            }
        }
    }

}
