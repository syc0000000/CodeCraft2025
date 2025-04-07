package MultiReader;

import IO.model.MultiReadCommandOut;

import java.util.HashSet;
import java.util.Iterator;

import IO.model.CompleteCommandOut;
import Info.Info;
import Info.model.*;
import Logger.Logger;
import Logger.LoggerFactory;

public class DefaultReader implements MultiReaderStrategy {
    // 第一个ptr在分割线左侧，第二个在分割线右侧
    public int left[][] = new int[2][10];
    public int right[][] = new int[2][10];

    @Override
    public void read(int diskId, MultiReadCommandOut readCommandOut, HashSet<CompleteCommandOut> completeCommandOuts) {

        int tokenleft[] = new int[2];
        tokenleft[0] = Info.tokenPerTick;
        tokenleft[1] = Info.tokenPerTick;
        LocalDisk disk = Info.localDiskTbl.get(diskId);
        for (int index = 0; index < 2; index++) {
            // readerLogger.debug("磁盘"+diskId+"磁头" + index + "的token" +
            // tokenleft[index]+"开始读");
            boolean hasPassOrRead = false;
            if (disk.ptr[index] > right[index][diskId]) {
                readCommandOut.actions.get(index).add(Action.JUMP);
                readCommandOut.jumpTargets.set(index, left[index][diskId]);
                disk.ptrDoAction(index, Action.JUMP, left[index][diskId]);
                tokenleft[index] -= Info.tokenPerTick;
            }
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
                // else{
                // readCommandOut.actions.get(index).add(Action.PASS);
                // disk.ptrDoAction(index, Action.PASS);
                // tokenleft[index] -= disk.pretoken[index];
                // }

                // 如果没有任务，则直接向后寻找
                int k;
                int closestTaskPosition = findClosestTask(index, disk);
                // 找任务，找到就直接退出，尝试处理任务
                // if (tokenleft[index] - disk.calculateToken(index, Action.READ) < 0)
                // break;
                k = closestTaskPosition - disk.ptr[index];
                readerLogger.debug("k " + k + " tokenleft " + tokenleft[index] + " disk.ptr[index] "
                        + disk.ptr[index] + " closestTaskPosition " + closestTaskPosition + "preoper "
                        + disk.preoper[index] + " pretoken " + disk.pretoken[index]);
                // for (k = 1; k < tokenleft[index] - 64; k++) {
                // if (disk.unitData.get(disk.ptr[index] + k).isInTask) {
                // readerLogger.debug("向后寻找到任务" + k);
                // break;
                // }
                // }
                // 寻找出了RWEnd的范围
                // 没找到

                // 判某几种情况
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
                    if (closestTaskPosition != -1 && !hasPassOrRead && (k > Info.tokenPerTick || k < 0)) {
                        readerLogger.debug("距离 > G，执行跳转到" + closestTaskPosition);
                        readCommandOut.actions.get(index).add(Action.JUMP);
                        readCommandOut.jumpTargets.set(index, closestTaskPosition);
                        disk.ptrDoAction(index, Action.JUMP, closestTaskPosition);
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

    private int findClosestTask(int index, LocalDisk disk) {
        int closestPosition = -1;

        for (int pos = disk.ptr[index]; pos <= right[index][disk.diskId]; pos++) {
            if (disk.unitData.get(pos).isInTask) {
                closestPosition = pos;
                break;
            }
        }
        if (closestPosition != -1) {
            return closestPosition;
        }

        for (int pos = left[index][disk.diskId]; pos < disk.ptr[index]; pos++) {
            if (disk.unitData.get(pos).isInTask) {
                closestPosition = pos;
                break;
            }
        }

        return closestPosition;
    }

    public DefaultReader() {
        for (int i = 0; i < 10; i++) {
            left[1][i] = Info.localDiskTbl.get(i).logicalRWEnd / 2 + 1;
            left[0][i] = 0;
            right[1][i] = Info.localDiskTbl.get(i).logicalRWEnd;
            right[0][i] = Info.localDiskTbl.get(i).logicalRWEnd / 2;
        }
    }
}
