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
    public int partition[] = new int[10];

    @Override
    public void read(int diskId, MultiReadCommandOut readCommandOut, HashSet<CompleteCommandOut> completeCommandOuts) {
        int tokenleft[] = new int[2];
        tokenleft[0] = Info.tokenPerTick;
        tokenleft[1] = Info.tokenPerTick;
        LocalDisk disk = Info.localDiskTbl.get(diskId);
        for (int index = 0; index < 2; index++) {
            // readerLogger.debug("磁盘"+diskId+"磁头" + index + "的token" +
            // tokenleft[index]+"开始读");
            if (index == 0) {
                // 第一个ptr
                if (disk.ptr[index] > partition[diskId]) {
                    readCommandOut.actions.get(index).add(Action.JUMP);
                    readCommandOut.jumpTargets.set(index, 0);
                    disk.ptrDoAction(index, Action.JUMP, 0);
                    tokenleft[index] -= Info.tokenPerTick;
                }
            } else {
                // 第二个ptr
                if (disk.ptr[index] > disk.logicalRWEnd || disk.ptr[index] < partition[diskId]) {
                    readCommandOut.actions.get(index).add(Action.JUMP);
                    readCommandOut.jumpTargets.set(index, partition[diskId]);
                    disk.ptrDoAction(index, Action.JUMP, partition[diskId]);
                    tokenleft[index] -= Info.tokenPerTick;
                }
            }
            while (tokenleft[index] > 0) {
                boolean isInTask = disk.unitData.get(disk.ptr[index]).isInTask;
                if (isInTask) {
                    if (tokenleft[index] > disk.calculateToken(index, Action.READ)) {
                        int objId = disk.unitData.get(disk.ptr[index]).objId;
                        int blockId = disk.unitData.get(disk.ptr[index]).blockId;
                        UserObject object = Info.objMap.get(objId);
                        // 有任务就直接处理
                        Iterator<ReadTask> iterator = object.readTasks.iterator();
                        while (iterator.hasNext()) {
                            ReadTask readTask = iterator.next();

                            if (readTask.blockNotFinished.contains(blockId)) {

                                readerLogger.debug("任务ID" + readTask.taskId);
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
                        continue;
                    } else {
                        // 如果token不够，则直接退出
                        break;
                    }
                }
                //如果没有任务，则直接向后寻找
                int k;
                int closestTaskPosition = findClosestTask(disk);
                // 找任务，找到就直接退出，尝试处理任务
                if (tokenNow - calculateToken(Action.READ, disk) < 0)
                    break;
                for (k = 1; k < tokenNow - 64; k++) {
                    if (disk.unitData.get(disk.ptr + k).isInTask) {
                        readerLogger.debug("向后寻找到任务");
                        break;
                    }
                }
                // 寻找出了RWEnd的范围
                // 没找到

                // 判某几种情况
                if (k == 1) {
                    if (disk.pretoken < 52 && tokenNow > calculateToken(Action.READ, disk)) {
                        processAction(Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    } else {
                        processAction(Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                    hasPassOrRead = true;
                } else if (k == 2 && tokenNow > calculateToken(Action.PASS, disk)) {
                    if (disk.pretoken < 34) {
                        processAction(Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    } else {
                        processAction(Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                    hasPassOrRead = true;
                } else if (k == 3 && tokenNow > calculateToken(Action.PASS, disk)) {
                    if (disk.pretoken < 28) {
                        processAction(Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    } else {
                        processAction(Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                    hasPassOrRead = true;
                }
                // 任务离得很远
                else {
                    readerLogger.debug("向后寻找不到任务");
                    int distance = Math.abs(closestTaskPosition - disk.ptr);
                    if (closestTaskPosition != -1 && !hasPassOrRead && distance > Info.tokenPerTick) {
                        readerLogger.debug("距离 > G，执行跳转到" + closestTaskPosition);
                        readCommandOut.actions.add(Action.JUMP);
                        readCommandOut.jumpTarget = closestTaskPosition;
                        disk.ptrDoAction(Action.JUMP, closestTaskPosition);
                        disk.preoper = Action.JUMP;
                        disk.pretoken = Info.tokenPerTick;
                        readCommandOuts.put(i, readCommandOut);
                        break;
                    } else {
                        while (k > 0) {
                            processAction(Action.PASS, disk, readCommandOut);
                            tokenNow -= disk.pretoken;
                            k--;
                        }
                    }
                }

            }
        }

    }

    public DefaultReader() {
        for (int i = 0; i < partition.length; i++) {
            partition[i] = Info.localDiskTbl.get(i).logicalRWEnd / 2;
        }
    }
}
