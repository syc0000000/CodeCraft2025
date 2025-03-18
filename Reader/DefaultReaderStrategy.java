package Reader;

import java.util.ArrayList;

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
        // TODO: 实现默认的读取策略
        ReadRetrun readRetrun  = new ReadRetrun();
        //添加所有的任务
        addReadTask(readCommandIns);
        int tickToken = Info.tokenPerTick;
        //遍历磁盘
        for(int i = 0; i < Info.diskNum; i++){
            //基础准备
            LocalDisk disk = Info.localDiskTbl.get(i);
            int tokenNow = tickToken;
            ReadCommandOut readCommandOut = new ReadCommandOut();
            readCommandOut.actions = new ArrayList<>();
            //准备消耗token
            while(tokenNow > 0){
                boolean isInTask = false;
                int objId = disk.unitData.get(disk.ptr).objId;
                int blockId = disk.unitData.get(disk.ptr).blockId;
                UserObject object = Info.objMap.get(objId);
                //判断是否有任务，如果有就直接进行处理，并且将是否有任务置为true
                Iterator<ReadTask> iterator = object.readTasks.iterator();
                while(iterator.hasNext()){
                    ReadTask readTask = iterator.next();
                    if(readTask.blockNotFinished.contains(blockId)){
                        isInTask = true;
                        readTask.blockNotFinished.remove(blockId);
                        readTask.blockFinished.add(blockId);
                    }
                }
                // if(isInTask){
                    
                // }
            }


        }
        return null;
    }
}
