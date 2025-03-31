package Reader;

import java.util.ArrayList;

import IO.model.CompleteCommandOut;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.ReadRetrun;
import Info.Info.UserObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import Info.Info;
import Info.Info.LocalDisk;
import Info.Info.ReadTask;

public class DefaultReaderStrategy implements ReaderStrategy {
    @Override
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        // TODO: 实现默认的读取策略
        ReadRetrun readRetrun = new ReadRetrun();
        // 添加所有的任务
        addReadTask(readCommandIns);
        int tickToken = Info.tokenPerTick;
        Map<Integer, ReadCommandOut> readCommandOuts = new HashMap<>();
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>();
        // 遍历磁盘
        for (int i = 0; i < Info.diskNum; i++) {
            // 基础准备

            boolean hasPassOrRead = false;
            LocalDisk disk = Info.localDiskTbl.get(i);
            int tokenNow = tickToken;
            ReadCommandOut readCommandOut = new ReadCommandOut();
            readCommandOut.actions = new ArrayList<>();
            // 如果检测到需要跳转，则直接跳转
            readerLogger.debug("磁盘编号" + i + "目前ptr位置为" + disk.ptr + "RWEnd位置为" + disk.RWEnd);
            if (disk.ptr > disk.RWEnd) {
                readerLogger.debug("指针跳转");
                readCommandOut.actions.add(Info.Action.JUMP);
                readCommandOut.jumpTarget = 0;
                disk.ptrDoAction(Info.Action.JUMP, 0);
                disk.preoper = Info.Action.JUMP;
                disk.pretoken = Info.tokenPerTick;
                readCommandOuts.put(i, readCommandOut);
                continue;
            }
            // 准备消耗token
            readerLogger.debug("token剩余" + tokenNow);
            while (tokenNow > 0) {
                if (disk.ptr > disk.RWEnd)
                    break;
                boolean isInTask = disk.unitData.get(disk.ptr).isInTask;

                if (isInTask) {
                    readerLogger.debug("寻找到任务");
                    // 如果token足够，则直接进行操作

                    if (tokenNow > calculateToken(Info.Action.READ, disk)) {
                        readerLogger.debug("token足够");
                        int objId = disk.unitData.get(disk.ptr).objId;
                        int blockId = disk.unitData.get(disk.ptr).blockId;
                        UserObject object = Info.objMap.get(objId);
                        // 有任务就直接处理
                        readerLogger.debug("objid为" + objId + "blockid为" + blockId);

                        Iterator<ReadTask> iterator = object.readTasks.iterator();
                        while (iterator.hasNext()) {
                            ReadTask readTask = iterator.next();
                            if (readTask.blockNotFinished.contains(blockId)) {
                                // 检测任务的完成
                                // 检测过期
                                readerLogger.debug("任务ID" + readTask.taskId);
                                // readerLogger.debug("任务是否完成"+readTask.blockNotFinished.isEmpty());
                                if (readTask.isTimeout()) {
                                    readerLogger.debug("任务过期: " + readTask.taskId);
                                    object.timeoutTasks.add(readTask.taskId);
                                    iterator.remove();
                                    continue;
                                }
                                // 处理块
                                readTask.blockNotFinished.remove(blockId);
                                readTask.blockFinished.add(blockId);
                                // readerLogger.debug("任务是否完成"+readTask.blockNotFinished.isEmpty());
                                if (readTask.blockNotFinished.isEmpty()) {
                                    readerLogger.debug("上报任务id" + readTask.taskId);
                                    completeCommandOuts.add(new CompleteCommandOut(readTask.taskId));
                                    iterator.remove();
                                }
                            }
                        }
                        // 设为没有任务
                        disk.unitData.get(disk.ptr).isInTask = false;
                        // 输出
                        readerLogger.debug("输出READ，ptr位置为" + disk.ptr + "块id为" + disk.unitData.get(disk.ptr).blockId);
                        processAction(Info.Action.READ, disk, readCommandOut);
                        hasPassOrRead = true;
                        // 减少token
                        tokenNow -= disk.pretoken;
                        continue;
                    }
                    // 如果token不足，则直接退出，等待下一个tick进行处理
                    else {
                        break;
                    }
                }
                // 如果是发现已经跑出范围，则直接退出
                int k;
                int closestTaskPosition = findClosestTask(disk);
                // 找任务，找到就直接退出，尝试处理任务
                if (tokenNow - calculateToken(Info.Action.READ, disk) < 0)
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
                    if (disk.pretoken < 52 && tokenNow > calculateToken(Info.Action.READ, disk)) {
                        processAction(Info.Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    } else {
                        processAction(Info.Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                    hasPassOrRead = true;
                } else if (k == 2 && tokenNow > calculateToken(Info.Action.PASS, disk)) {
                    if (disk.pretoken < 34) {
                        processAction(Info.Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Info.Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    } else {
                        processAction(Info.Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Info.Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                    hasPassOrRead = true;
                } else if (k == 3 && tokenNow > calculateToken(Info.Action.PASS, disk)) {
                    if (disk.pretoken < 28) {
                        processAction(Info.Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Info.Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Info.Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    } else {
                        processAction(Info.Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Info.Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Info.Action.PASS, disk, readCommandOut);
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
                        readCommandOut.actions.add(Info.Action.JUMP);
                        readCommandOut.jumpTarget = closestTaskPosition;
                        disk.ptrDoAction(Info.Action.JUMP, closestTaskPosition);
                        disk.preoper = Info.Action.JUMP;
                        disk.pretoken = Info.tokenPerTick;
                        readCommandOuts.put(i, readCommandOut);
                        break;
                    } else {
                        while (k > 0) {
                            processAction(Info.Action.PASS, disk, readCommandOut);
                            tokenNow -= disk.pretoken;
                            k--;
                        }
                    }
                }
            }

            readCommandOuts.put(i, readCommandOut);
        }
        readRetrun.readCommandOuts = readCommandOuts;
        readRetrun.completeCommandOuts = completeCommandOuts;
        return readRetrun;
    }

    private int findClosestTask(LocalDisk disk) {
        int closestPosition = -1;

        for (int pos = disk.ptr; pos <= disk.logicalRWEnd; pos++) {
            if (disk.unitData.get(pos).isInTask) {
                closestPosition = pos;
                break;
            }
        }
        if (closestPosition != -1) {
            return closestPosition;
        }

        for (int pos = 0; pos < disk.ptr; pos++) {
            if (disk.unitData.get(pos).isInTask) {
                closestPosition = pos;
                break;
            }
        }

        return closestPosition;
    }
}
