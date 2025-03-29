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
import Info.Info.Action;
import Info.Info.LocalDisk;
import Info.Info.ReadTask;
import Info.Info.UserObject;

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
            LocalDisk disk = Info.localDiskTbl.get(i);
            int tokenNow = tickToken;
            ReadCommandOut readCommandOut = new ReadCommandOut();
            readCommandOut.actions = new ArrayList<>();

            // 如果指针超出了磁盘范围，跳转到0
            if (disk.ptr > disk.RWEnd) {
                readerLogger.debug("指针超出范围，跳转到0");
                readCommandOut.actions.add(Action.JUMP);
                readCommandOut.jumpTarget = 0;
                disk.ptrDoAction(Action.JUMP, 0);
                disk.preoper = Action.JUMP;
                disk.pretoken = Info.tokenPerTick;
                readCommandOuts.put(i, readCommandOut);
                continue;
            }

            // 根据策略执行操作
            while (tokenNow > 0) {
                if (disk.ptr > disk.RWEnd)
                    break;
                // 找到离磁头最近的任务
                int closestTaskPosition = findClosestTask(disk);
                readerLogger
                        .debug("磁盘" + i + "当前位置:" + disk.ptr + " 最近任务位置:" + closestTaskPosition);

                int distance = closestTaskPosition - disk.ptr;

                // 距离大于G，直接跳转
                if (distance > Info.tokenPerTick) {
                    // 如果距离 > G，就跳转
                    readerLogger.debug("距离 > G，执行跳转到" + closestTaskPosition);
                    readCommandOut.actions.add(Info.Action.JUMP);
                    readCommandOut.jumpTarget = 0;
                    disk.ptrDoAction(Info.Action.JUMP, 0);
                    disk.preoper = Info.Action.JUMP;
                    disk.pretoken = Info.tokenPerTick;
                    readCommandOuts.put(i, readCommandOut);
                    break;
                }

                // 根据距离不同，选择pass过去还是read过去
                if (distance > (2.0 / 3.0) * Info.tokenPerTick) {
                    // 如果距离 > 2/3 G，就PASS
                    readerLogger.debug("距离 > 2/3 G，执行PASS");
                    while (tokenNow > 0 && disk.ptr < closestTaskPosition) {
                        processAction(Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                } else {
                    readerLogger.debug("距离 <= 2/3 G，执行READ操作");
                    while (tokenNow > 0 && disk.ptr < closestTaskPosition) {
                        // 计算读取操作消耗的token
                        processAction(Info.Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                }

                // 如果磁头到达了任务位置
                if (disk.ptr == closestTaskPosition
                        && tokenNow > calculateToken(Info.Action.READ, disk)) {
                    // 到达任务位置，执行READ操作
                    readerLogger.debug("到达任务位置，执行READ操作");
                    tokenNow -= disk.pretoken;
                    int objId = disk.unitData.get(disk.ptr).objId;
                    int blockId = disk.unitData.get(disk.ptr).blockId;
                    UserObject object = Info.objMap.get(objId);
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
                    disk.unitData.get(disk.ptr).isInTask = false;
                    // 输出
                    readerLogger.debug("输出READ，ptr位置为" + disk.ptr + "块id为"
                            + disk.unitData.get(disk.ptr).blockId);
                    processAction(Info.Action.READ, disk, readCommandOut);
                    tokenNow -= disk.pretoken;
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

        for (int pos = disk.ptr; pos <= disk.RWEnd; pos++) {
            if (disk.unitData.get(pos).isInTask) {
                closestPosition = pos;
                break;
            }
        }

        return closestPosition;
    }
}
