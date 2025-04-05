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
                if (disk.ptr[index] < disk.logicalRWEnd) {
                    readCommandOut.actions.get(index).add(Action.JUMP);
                    readCommandOut.jumpTargets.set(index, partition[diskId]);
                    disk.ptrDoAction(index, Action.JUMP, partition[diskId]);
                    tokenleft[index] -= Info.tokenPerTick;
                }
            }
            while (tokenleft[index] > 0) {
                if (disk.ptr[0] > partition[diskId])
                    break;
                boolean isInTask = disk.unitData.get(disk.ptr[index]).isInTask;
                if (isInTask) {
                    if (tokenleft[index] > disk.calculateToken(index, Action.READ)) {
                        readerLogger.debug("token足够");
                        int objId = disk.unitData.get(disk.ptr[index]).objId;
                        int blockId = disk.unitData.get(disk.ptr[index]).blockId;
                        UserObject object = Info.objMap.get(objId);
                        // 有任务就直接处理
                        readerLogger.debug("objid为" + objId + "blockid为" + blockId);

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
                } else {
                    // 如果没有任务，则直接跳过
                    readCommandOut.actions.get(index).add(Action.PASS);
                    disk.ptrDoAction(index, Action.PASS);
                    tokenleft[index] -= disk.pretoken[index];
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
