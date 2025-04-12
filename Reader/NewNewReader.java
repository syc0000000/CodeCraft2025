package Reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import IO.model.CompleteCommandOut;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.ReadRetrun;
import Info.Info;
import Info.model.Action;
import Info.model.LocalDisk;
import Info.model.ReadTask;
import Info.model.UserObject;

public class NewNewReader implements ReaderStrategy {
    @Override
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        ReadRetrun readRetrun = new ReadRetrun();
        // 添加所有的任务
        addReadTask(readCommandIns);
        int tickToken = Info.tokenPerTick;
        Map<Integer, ReadCommandOut> readCommandOuts = new HashMap<>();
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>();

        // 遍历磁盘
        for (int i = 0; i < Info.diskNum; i++) {
            readerLogger.debug("磁盘" + i + "开始处理");
            LocalDisk disk = Info.localDiskTbl.get(i);
            int tokenNow = tickToken;
            ReadCommandOut readCommandOut = new ReadCommandOut();
            readCommandOut.actions = new ArrayList<>();

            // 如果指针超出了磁盘范围，跳转到0
            if (disk.ptr > disk.logicalRWEnd) {
                readerLogger.debug("指针超出范围，跳转到0");
                readCommandOut.actions.add(Action.JUMP);
                readCommandOut.jumpTarget = 0;
                disk.ptrDoAction(Action.JUMP, 0);
                disk.preoper = Action.JUMP;
                disk.pretoken = Info.tokenPerTick;
                readCommandOuts.put(i, readCommandOut);
                continue;
            }

            boolean hasReadOrPass = false;
            // 根据策略执行操作
            while (tokenNow > 0) {
                if (disk.ptr > disk.RWEnd)
                    break;
                // 找到离磁头最近的任务
                int closestTaskPosition = findClosestTask(disk);
                // 如果找不到任务,pass一下
                if (closestTaskPosition == -1) {
                    break;
                }

                readerLogger
                        .debug("磁盘" + i + "当前位置:" + disk.ptr + " 最近任务位置:" + closestTaskPosition);

                int distance = closestTaskPosition - disk.ptr;

                // 如果距离小于0或者大于G，跳转到最近任务位置
                if ((distance < 0 || distance > Info.tokenPerTick) && !hasReadOrPass) {
                    readerLogger.debug("距离 > G，执行跳转到" + closestTaskPosition);
                    readCommandOut.actions.add(Action.JUMP);
                    readCommandOut.jumpTarget = closestTaskPosition;
                    disk.ptrDoAction(Action.JUMP, closestTaskPosition);
                    disk.preoper = Action.JUMP;
                    disk.pretoken = Info.tokenPerTick;
                    readCommandOuts.put(i, readCommandOut);
                    break;
                } else if (distance < 0 || distance > Info.tokenPerTick) {
                    break;
                }

                // 根据距离不同，选择pass/read到有任务的位置
                if (distance > (2.0 / 3.0) * Info.tokenPerTick) {
                    // 如果距离 > 2/3 G，就PASS
                    readerLogger.debug("距离 > 2/3 G");
                    int tokenNeeded = calculateToken(Action.PASS, disk);
                    if (tokenNow < tokenNeeded) {
                        readerLogger.debug("token不足，跳过当前磁盘处理: tokenNow=" + tokenNow + ", needed="
                                + tokenNeeded);
                        break;
                    }
                    while (disk.ptr < closestTaskPosition && tokenNow >= tokenNeeded) {
                        readerLogger.debug("token剩余" + tokenNow + " 距离任务位置"
                                + (closestTaskPosition - disk.ptr));
                        processAction(Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        tokenNeeded = calculateToken(Action.PASS, disk);
                        hasReadOrPass = true;
                    }
                } else {
                    readerLogger.debug("距离 <= 2/3 G");
                    int tokenNeeded = calculateToken(Action.READ, disk);
                    if (tokenNow < tokenNeeded) {
                        readerLogger.debug("token不足，跳过当前磁盘处理: tokenNow=" + tokenNow + ", needed="
                                + tokenNeeded);
                        break;
                    }
                    while (disk.ptr <= closestTaskPosition && tokenNow >= tokenNeeded) {
                        readerLogger.debug("token剩余" + tokenNow + " 距离任务位置"
                                + (closestTaskPosition - disk.ptr));
                        int objId = disk.unitData.get(disk.ptr).objId;
                        int blockId = disk.unitData.get(disk.ptr).blockId;
                        if (objId == -1) {
                            readerLogger.debug("输出READ，ptr位置为" + disk.ptr + "块id为"
                                    + disk.unitData.get(disk.ptr).blockId + "浪费token" + disk.pretoken);
                            processAction(Action.READ, disk, readCommandOut);
                            tokenNow -= disk.pretoken;
                            hasReadOrPass = true;
                            continue;
                        }
                        UserObject object = Info.objMap.get(objId);
                        readerLogger.debug("objid为" + objId + "blockid为" + blockId + "objsize为 "
                                + object.objSize);
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
                                    completeCommandOuts
                                            .add(new CompleteCommandOut(readTask.taskId));
                                    iterator.remove();
                                }
                            }
                        }
                        disk.unitData.get(disk.ptr).isInTask = false;
                        // 输出
                        readerLogger.debug("输出READ，ptr位置为" + disk.ptr + "块id为"
                                + disk.unitData.get(disk.ptr).blockId + "对象id为" + object.objId);
                        processAction(Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        hasReadOrPass = true;

                    }
                }
            }
            readCommandOuts.put(i, readCommandOut);
        }
        readRetrun.readCommandOuts = readCommandOuts;
        readRetrun.completeCommandOuts = completeCommandOuts;
        return readRetrun;
    }

    // 辅助方法：找到离当前磁头最近的任务位置
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
