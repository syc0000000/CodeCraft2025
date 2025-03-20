package Reader;

import java.util.ArrayList;

import IO.model.CompleteCommandOut;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.ReadRetrun;
import Info.Info.UserObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import Info.Info;
import Info.Info.Action;
import Info.Info.LocalDisk;
import Info.Info.ReadTask;
import Info.Info.UserObject;

public class NewReaderStratrgy implements ReaderStrategy {
    @Override
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        // TODO: 实现默认的读取策略
        ReadRetrun readRetrun = new ReadRetrun();
        // 添加所有的任务
        addReadTask(readCommandIns);
        int tickToken = Info.tokenPerTick;
        Map<Integer, ReadCommandOut> readCommandOuts = new HashMap<>();
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>();
        readerLogger.debug("进入读取模块");
        // 遍历磁盘
        for (int i = 0; i < Info.diskNum; i++) {
            // 基础准备
            LocalDisk disk = Info.localDiskTbl.get(i);
            int tokenNow = tickToken;
            ReadCommandOut readCommandOut = new ReadCommandOut();
            if(Info.timestamp < 100) continue;
            if(disk.ptr > disk.RWEnd){
                readCommandOut.actions.add(Info.Action.JUMP);
                disk.preoper = Info.Action.JUMP;
                readCommandOut.jumpTarget = 0;
                disk.ptr = 0;
            }
            ArrayList<Action> actionLast = new ArrayList<>();
            actionLast.add(disk.preoper);
            if(i == 8)readerLogger.debug("剩余token为"+tokenNow);
            readCommandOut.actions = new ArrayList<>(findStrategy(disk, disk.ptr, tokenNow,actionLast, disk.pretoken));
            readCommandOut.actions.remove(0);
            disk.preoper = readCommandOut.actions.get(readCommandOut.actions.size() - 1);
            
            for(int j = 0; j< readCommandOut.actions.size(); j++){
                if(disk.unitData.get(disk.ptr + j).isInTask){
                    disk.unitData.get(disk.ptr+j).isInTask = false;
                    int blockId = disk.unitData.get(disk.ptr + j).blockId;
                    int objId = disk.unitData.get(disk.ptr + j).objId;
                    UserObject obj = Info.objMap.get(objId);
                    Iterator<ReadTask> iterator = obj.readTasks.iterator();
                    while(iterator.hasNext()){
                        ReadTask readTask = iterator.next();
                        if (readTask.blockNotFinished.contains(blockId)) {
                            // 检测任务的完成
                            // 检测过期
                            //readerLogger.debug("任务ID"+readTask.taskId);
                            //readerLogger.debug("任务是否完成"+readTask.blockNotFinished.isEmpty());
                            if (readTask.isTimeout()) {
                            readerLogger.debug("任务过期: " + readTask.taskId);
                                obj.timeoutTasks.add(readTask.taskId);
                                iterator.remove();
                                continue;
                            }
                            // 处理块
                            readTask.blockNotFinished.remove(blockId);
                            //readerLogger.debug("任务ID"+readTask.taskId+"没有读的块"+readTask.blockNotFinished.size());
                            readTask.blockFinished.add(blockId);
                            //readerLogger.debug("任务是否完成"+readTask.blockNotFinished.isEmpty());
                            if (readTask.blockNotFinished.isEmpty()) {
                                //readerLogger.debug("上报任务id"+readTask.taskId);
                                completeCommandOuts.add(new CompleteCommandOut(readTask.taskId));
                                iterator.remove();
                            }
                        }
                    }
                }
               
            }
            disk.ptr++;
            // 如果检测到需要跳转，则直接跳转
            readerLogger.debug("磁盘编号"+i+"目前ptr位置为"+disk.ptr+"RWEnd位置为"+disk.RWEnd);
            readCommandOuts.put(i, readCommandOut);
        }
        readRetrun.readCommandOuts = readCommandOuts;
        readRetrun.completeCommandOuts = completeCommandOuts;
        return readRetrun;
    }
}
