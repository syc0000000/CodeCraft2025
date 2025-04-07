package MultiReader;

import java.util.HashSet;
import java.util.Iterator;

import IO.model.CompleteCommandOut;
import IO.model.MultiReadCommandOut;
import Info.model.LocalDisk;
import Info.model.ReadTask;
import Info.model.UserObject;
import Info.Info;
import Logger.LoggerFactory;
import Info.model.Action;

public class ReadOnlyReader implements MultiReaderStrategy {
        public int partition[] = new int[10];

    @Override
    public void read(int diskId, MultiReadCommandOut readCommandOut, HashSet<CompleteCommandOut> completeCommandOuts) {
        int tokenleft[] = new int[2];
        tokenleft[0] = Info.tokenPerTick;
        tokenleft[1] = Info.tokenPerTick;
        LocalDisk disk = Info.localDiskTbl.get(diskId);
        for (int index = 0; index < 2; index++) {
            readerLogger.debug("磁盘"+diskId+"磁头" + index + "的token" +
            tokenleft[index]+"开始读");
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
                if (tokenleft[index] > disk.calculateToken(index, Action.READ)) {
                    disk.unitData.get(disk.ptr[index]).isInTask = false;
                    // 输出
                    readCommandOut.actions.get(index).add(Action.READ);
                    disk.ptrDoAction(index, Action.READ);
                    tokenleft[index] -= disk.pretoken[index];

                    int objId = disk.unitData.get(disk.ptr[index]).objId;
                    if(objId == -1) {
                        // 读到空闲块
                        continue;
                    }
                    int blockId = disk.unitData.get(disk.ptr[index]).blockId;
                    UserObject object = Info.objMap.get(objId);
                    // 有任务就直接处理
                    if(object.readTasks.isEmpty()) {
                        // 读到空闲块
                        continue;
                    }
                    Iterator<ReadTask> iterator = object.readTasks.iterator();
                    while (iterator.hasNext()) {
                        ReadTask readTask = iterator.next();
                            readerLogger.debug(index+"读到块id"+blockId);
                            readerLogger.debug("位置"+disk.ptr[index]);
                        if (readTask.blockNotFinished.contains(blockId)) {

                            readerLogger.debug("任务ID" + readTask.taskId);
                            // 处理块
                            readTask.blockNotFinished.remove(blockId);
                            readTask.blockFinished.add(blockId);
                            readerLogger.debug("任务是否完成"+readTask.blockNotFinished.isEmpty());
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
                } 
                else {
                    // 如果token不够，则直接退出
                    break;
                }
            }
        }

    }

    public ReadOnlyReader() {
        for (int i = 0; i < partition.length; i++) {
            partition[i] = Info.localDiskTbl.get(i).logicalRWEnd / 2;
        }
    }
}
