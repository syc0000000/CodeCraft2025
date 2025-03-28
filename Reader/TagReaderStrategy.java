package Reader;

import java.util.ArrayList;
import java.util.Comparator;

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

public class TagReaderStrategy implements ReaderStrategy {
    public class TagInfo {
        int tagid;
        int middle;
        int left;
        int end;
        public int getMiddle(){
            return middle;
        }
    }
    public ArrayList<ArrayList<TagInfo>> tagInfo = new ArrayList<>();
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
            // 基础准备
            //本磁盘的tag从前到后分布
            ArrayList<TagInfo> tagInfos = tagInfo.get(i);
            LocalDisk disk = Info.localDiskTbl.get(i);
            int tokenNow = tickToken;
            ReadCommandOut readCommandOut = new ReadCommandOut();
            readCommandOut.actions = new ArrayList<>();
            // 如果检测到需要跳转，则直接跳转
            readerLogger.debug("磁盘编号"+i+"目前ptr位置为"+disk.ptr+"RWEnd位置为"+disk.RWEnd);
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
            readerLogger.debug("token剩余"+tokenNow);
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
                        Iterator<ReadTask> iterator = object.readTasks.iterator();
                        while (iterator.hasNext()) {
                            ReadTask readTask = iterator.next();
                            if (readTask.blockNotFinished.contains(blockId)) {
                                // 检测任务的完成
                                // 检测过期
                                if (readTask.isTimeout()) {
                                    readerLogger.debug("任务过期: " + readTask.taskId);
                                    object.timeoutTasks.add(readTask.taskId);
                                    iterator.remove();
                                    continue;
                                }
                                // 处理块
                                readTask.blockNotFinished.remove(blockId);
                                readTask.blockFinished.add(blockId);

                                if (readTask.blockNotFinished.isEmpty()) {
                                    completeCommandOuts.add(new CompleteCommandOut(readTask.taskId));
                                    iterator.remove();
                                }
                            }
                        }
                        // 设为没有任务
                        disk.unitData.get(disk.ptr).isInTask = false;
                        // 输出
                        processAction(Info.Action.READ, disk, readCommandOut);
                        // 减少token
                        tokenNow -= disk.pretoken;
                        continue;
                    }
                    // 如果token不足，则直接退出，等待下一个tick进行处理
                    else {
                        break;
                    }
                }
                // 如果没有任务，则直接跳过
                else {
                    processAction(Info.Action.PASS, disk, readCommandOut);
                    tokenNow -= disk.pretoken;
                }
            }

            readCommandOuts.put(i, readCommandOut);
        }
        readRetrun.readCommandOuts = readCommandOuts;
        readRetrun.completeCommandOuts = completeCommandOuts;
        return readRetrun;
    }
    public static class middleComparator implements Comparator<TagInfo> {
        @Override
        public int compare(TagInfo a, TagInfo b) {
            return a.middle - b.middle;
        }
    }
    public TagReaderStrategy(){

        for(int i = 0; i < Info.diskNum;i++){
            ArrayList<TagInfo> tagInfos = new ArrayList<>();
            for(int j = 0; j < Info.tagNums;j++){
                TagInfo taginfo = new TagInfo();
                taginfo.tagid = j;
                taginfo.middle = Info.tags.get(j).middleList.get(i);
                tagInfos.add(taginfo);
            }
            tagInfos.sort(new middleComparator());
            tagInfo.add(tagInfos);
        }
    }
}
