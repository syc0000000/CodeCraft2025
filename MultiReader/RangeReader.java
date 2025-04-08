package MultiReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
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
    }

    public RangeReader(ArrayList<HashSet<Integer>> periodToTagSet) {
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
                    int start = tagMeta.left;
                    int end = tagMeta.right;
                    ranges.add(new Range(start, end, diskId));
                    // rangeList.get(period).get(diskId).get(ptrId).add(new Range(start, end,
                    // diskId));
                }
                // 排序(从小到大)
                ranges.sort((a, b) -> a.start - b.start);
                // 分出两个磁头的range
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
                ArrayList<Range> leftRanges = new ArrayList<>();
                ArrayList<Range> rightRanges = new ArrayList<>();
                for (Range range : ranges) {
                    if (range.start < splitPoint) {
                        leftRanges.add(range);
                    } else {
                        rightRanges.add(range);
                    }
                }
                // 两个磁头的range
                rangeList.get(period).get(diskId).add(leftRanges);
                rangeList.get(period).get(diskId).add(rightRanges);
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

    // 计算这一tick的目的地，考虑任务、range、磁头位置
    public int calculateTarget(int index, LocalDisk disk) {
        int period = Info.timestamp / 1800;
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
    public void read(int diskId, MultiReadCommandOut readCommandOut, HashSet<CompleteCommandOut> completeCommandOuts) {
        LocalDisk disk = Info.localDiskTbl.get(diskId);
        int tokenleft[] = new int[2];
        tokenleft[0] = Info.tokenPerTick;
        tokenleft[1] = Info.tokenPerTick;
        for (int index = 0; index < 2; index++) {
            boolean hasPassOrRead = false;
            while (tokenleft[index] >= 0) {
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

                                readerLogger.debug("硬盘" + diskId + "任务ID" + readTask.taskId + "块ID" + blockId + "磁头"
                                        + index + "读到块" + disk.ptr[index]);
                                // 处理块
                                readTask.blockNotFinished.remove(blockId);
                                readTask.blockFinished.add(blockId);
                                // readerLogger.debug("任务是否完成"+readTask.blockNotFinished.isEmpty());
                                if (readTask.blockNotFinished.isEmpty()) {
                                    readerLogger.debug("上报任务id" + readTask.taskId);
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
                readerLogger.debug("k " + k + " tokenleft " + tokenleft[index] + " disk.ptr[index] "
                        + disk.ptr[index] + " closestTaskPosition " + target + "preoper "
                        + disk.preoper[index] + " pretoken " + disk.pretoken[index]);
                if (tokenleft[index] == 0) {
                    // 解决coner case k>0 preoper是read，但tokenleft[index]==0
                    break;
                }
                if (k == 1) {
                    if (disk.preoper[index] == Action.READ && disk.pretoken[index] < 52
                            && tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                        readCommandOut.actions.get(index).add(Action.READ);
                        disk.ptrDoAction(index, Action.READ);
                        tokenleft[index] -= disk.pretoken[index];
                        hasPassOrRead = true;
                    } else if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                        readCommandOut.actions.get(index).add(Action.PASS);
                        disk.ptrDoAction(index, Action.PASS);
                        tokenleft[index] -= disk.pretoken[index];
                        hasPassOrRead = true;
                    }
                } else if (k == 2) {
                    if (disk.preoper[index] == Action.READ && disk.pretoken[index] < 34
                            && tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                        readCommandOut.actions.get(index).add(Action.READ);
                        disk.ptrDoAction(index, Action.READ);
                        tokenleft[index] -= disk.pretoken[index];
                        hasPassOrRead = true;
                        if (tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                            readCommandOut.actions.get(index).add(Action.READ);
                            disk.ptrDoAction(index, Action.READ);
                            tokenleft[index] -= disk.pretoken[index];
                        }
                    } else if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                        readCommandOut.actions.get(index).add(Action.PASS);
                        disk.ptrDoAction(index, Action.PASS);
                        tokenleft[index] -= disk.pretoken[index];
                        hasPassOrRead = true;
                        if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                            readCommandOut.actions.get(index).add(Action.PASS);
                            disk.ptrDoAction(index, Action.PASS);
                            tokenleft[index] -= disk.pretoken[index];
                        }
                    }
                } else if (k == 3) {
                    if (disk.preoper[index] == Action.READ && disk.pretoken[index] < 28
                            && tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                        readCommandOut.actions.get(index).add(Action.READ);
                        disk.ptrDoAction(index, Action.READ);
                        tokenleft[index] -= disk.pretoken[index];
                        hasPassOrRead = true;
                        if (tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                            readCommandOut.actions.get(index).add(Action.READ);
                            disk.ptrDoAction(index, Action.READ);
                            tokenleft[index] -= disk.pretoken[index];
                        }
                        if (tokenleft[index] >= disk.calculateToken(index, Action.READ)) {
                            readCommandOut.actions.get(index).add(Action.READ);
                            disk.ptrDoAction(index, Action.READ);
                            tokenleft[index] -= disk.pretoken[index];
                        }
                    } else if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                        readCommandOut.actions.get(index).add(Action.PASS);
                        disk.ptrDoAction(index, Action.PASS);
                        tokenleft[index] -= disk.pretoken[index];
                        hasPassOrRead = true;
                        if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                            readCommandOut.actions.get(index).add(Action.PASS);
                            disk.ptrDoAction(index, Action.PASS);
                            tokenleft[index] -= disk.pretoken[index];
                        }
                        if (tokenleft[index] >= disk.calculateToken(index, Action.PASS)) {
                            readCommandOut.actions.get(index).add(Action.PASS);
                            disk.ptrDoAction(index, Action.PASS);
                            tokenleft[index] -= disk.pretoken[index];
                        }
                    }
                }
                // 任务离得很远
                else {
                    readerLogger.debug("向后寻找不到任务");
                    if (target != -1 && !hasPassOrRead && (k > Info.tokenPerTick || k < 0)) {
                        readerLogger.debug("距离 > G，执行跳转到" + target);
                        readCommandOut.actions.get(index).add(Action.JUMP);
                        readCommandOut.jumpTargets.set(index, target);
                        disk.ptrDoAction(index, Action.JUMP, target);
                        tokenleft[index] -= Info.tokenPerTick;
                        break;
                    } else if (k < 0) {
                        // 这里实际上是两种情况，一种是整个盘就没有任务，k=-1-disk.ptr[index]
                        // 一种是后面的任务已经被消化完，盘上有任务，但在ptr前面，但是又因为已经有过其他操作，所以不能跳转
                        break;
                    } else {
                        // 此时，说明盘上有任务
                        readerLogger.debug("盘上有任务，执行pass " + k + " tokenleft " + tokenleft[index]);
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
