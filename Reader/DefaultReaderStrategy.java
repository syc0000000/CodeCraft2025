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
            //如果检测到需要跳转，则直接跳转
            if(disk.ptr > disk.RWEnd){
                readCommandOut.actions.add(Info.Action.JUMP);
                readCommandOut.jumpTarget = 0;
                disk.ptrDoAction(Info.Action.JUMP, 0);
                disk.preoper = Info.Action.JUMP;
                disk.pretoken = Info.tokenPerTick;
                continue;
            }
            //准备消耗token
            while(tokenNow > 0){
                if(disk.ptr > disk.RWEnd) break;
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
                        processAction(Info.Action.READ, disk, readCommandOut);
                        //减少token
                        tokenNow -= disk.pretoken;
                        continue;
                    }
                    //如果token不足，则直接退出，等待下一个tick进行处理
                    else{
                        break;
                    }
                }
                //如果是发现已经跑出范围，则直接退出
                int k;
                int restrict = (disk.RWEnd - disk.ptr + 2) < (tokenNow - 63) ? disk.RWEnd - disk.ptr : tokenNow - 63;
                //找任务，找到就直接退出，尝试处理任务
                for(k = 1; k < restrict ; k++){
                    if(disk.unitData.get(disk.ptr + k).isInTask){
                        break;
                    }
                }
                //寻找出了RWEnd的范围
                if(disk.ptr + k > disk.RWEnd) break;
                //没找到
                if(!disk.unitData.get(disk.ptr + k).isInTask) break;
                //判某几种情况
                if(k == 1){
                    if(disk.pretoken < 52){
                        processAction(Info.Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                    else{
                        processAction(Info.Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                }
                else if(k == 2){
                    if(disk.pretoken < 34){
                        processAction(Info.Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Info.Action.READ, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                    else{
                        processAction(Info.Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        processAction(Info.Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                    }
                }
                //任务离得很远
                else{
                    while(k > 0){
                        processAction(Info.Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
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
