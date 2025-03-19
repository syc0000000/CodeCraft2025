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
import Info.Info.LocalDisk;
import Info.Info.ReadTask;
import Info.Info.UserObject;


public class DefaultReaderStrategy implements ReaderStrategy {
    @Override
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        //TODO: 实现默认的读取策略
        ReadRetrun readRetrun  = new ReadRetrun();
        //添加所有的任务
        addReadTask(readCommandIns);
        int tickToken = Info.tokenPerTick;
        Map<Integer, ReadCommandOut> readCommandOuts = new HashMap<>();
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>();
        //遍历磁盘
        for(int i = 0; i < Info.diskNum; i++){
            //基础准备
            LocalDisk disk = Info.localDiskTbl.get(i);
            int tokenNow = tickToken;
            ReadCommandOut readCommandOut = new ReadCommandOut();
            readCommandOut.actions = new ArrayList<>();
            
            //准备消耗token
            while(tokenNow > 0){
                boolean isInTask = disk.unitData.get(disk.ptr).isInTask;
                if(isInTask){
                    //如果token足够，则直接进行操作
                    if(tokenNow > calculateToken(Info.Action.READ, disk)){
                        int objId = disk.unitData.get(disk.ptr).objId;
                        int blockId = disk.unitData.get(disk.ptr).blockId;
                        UserObject object = Info.objMap.get(objId);
                        //有任务就直接处理
                        Iterator<ReadTask> iterator = object.readTasks.iterator();
                        while(iterator.hasNext()){
                            ReadTask readTask = iterator.next();
                            if(readTask.blockNotFinished.contains(blockId)){
                                //检测任务的完成
                                //检测过期
                                if(readTask.isTimeout()){
                                    readerLogger.debug("任务过期: " + readTask.taskId);
                                    object.timeoutTasks.add(readTask.taskId);
                                    iterator.remove();
                                    continue;
                                }
                                //处理块
                                readTask.blockNotFinished.remove(blockId);
                                readTask.blockFinished.add(blockId);
                                if(readTask.blockFinished.isEmpty()){
                                    completeCommandOuts.add(new CompleteCommandOut(readTask.taskId));
                                }
                            }   
                        }
                        //设为没有任务
                        disk.unitData.get(disk.ptr).isInTask = false;
                        //输出
                        readCommandOut.actions.add(Info.Action.READ);
                        //token操作
                        disk.pretoken = calculateToken(Info.Action.READ, disk);
                        disk.preoper = Info.Action.READ;
                        tokenNow -= disk.pretoken;
                        //指针操作
                        disk.ptrDoAction(Info.Action.READ);
                        continue;
                    }
                    //如果token不足，则直接退出，等待下一个tick进行处理
                    else{
                        break;
                    }
                }
                int k;
                int restrict = (disk.RWEnd - disk.ptr + 1) < (tokenNow - 63) ? disk.RWEnd - disk.ptr : tokenNow - 63;
                for(k = 1; k < restrict ; k++){
                    if(disk.unitData.get(disk.ptr + k).isInTask){
                        break;
                    }
                }
                if(k == 1){
                    if(disk.pretoken < 52){
                        readCommandOut.actions.add(Info.Action.READ);
                        disk.pretoken = calculateToken(Info.Action.READ, disk);
                        disk.preoper = Info.Action.READ;
                        tokenNow -= disk.pretoken;
                        disk.ptrDoAction(Info.Action.READ);
                    }
                }
                else if(k == 2){
                    if(disk.pretoken < 34){
                        readCommandOut.actions.add(Info.Action.READ);
                        disk.pretoken = calculateToken(Info.Action.READ, disk);
                        disk.preoper = Info.Action.READ;
                        tokenNow -= disk.pretoken;
                        disk.ptrDoAction(Info.Action.READ);
                        readCommandOut.actions.add(Info.Action.READ);
                        disk.pretoken = calculateToken(Info.Action.READ, disk);
                        disk.preoper = Info.Action.READ;
                        tokenNow -= disk.pretoken;
                        disk.ptrDoAction(Info.Action.READ);
                    }
                }
                else{
                    while(k > 0){
                        readCommandOut.actions.add(Info.Action.PASS);
                        disk.pretoken = calculateToken(Info.Action.PASS, disk);
                        disk.preoper = Info.Action.PASS;
                        tokenNow -= disk.pretoken;
                        disk.ptrDoAction(Info.Action.PASS);
                        k--;
                    }
                }
            }

            readCommandOuts.put(i,readCommandOut);
        }
        readRetrun.readCommandOuts = readCommandOuts;
        readRetrun.completeCommandOuts = completeCommandOuts;
        return null;
    }
}
