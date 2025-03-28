package Reader;

import java.util.ArrayList;
import java.util.Comparator;

import IO.model.CompleteCommandOut;
import IO.model.ReadCommandIn;
import IO.model.ReadCommandOut;
import IO.model.ReadRetrun;
import Info.Info.UserObject;
import Logger.LoggerFactory.ModuleLogger;
import Logger.LoggerFactory;
import IO.IO;
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
    ModuleLogger tagReaderLogger = LoggerFactory.getLogger("TagReader");

    public class TagInfo {
        int tagid;
        int middle;
        int left;
        int end;

        public int getMiddle() {
            return middle;
        }
    }

    public ArrayList<ArrayList<TagInfo>> tagInfo = new ArrayList<>();

    @Override
    public ReadRetrun read(ArrayList<ReadCommandIn> readCommandIns) {
        ReadRetrun readRetrun = new ReadRetrun();
        int period = Info.timestamp / 1800;
        if (period > 47)
            period = 47;
        // 添加所有的任务
        addReadTask(readCommandIns);
        int tickToken = Info.tokenPerTick;
        Map<Integer, ReadCommandOut> readCommandOuts = new HashMap<>();
        HashSet<CompleteCommandOut> completeCommandOuts = new HashSet<>();
        // 遍历磁盘
        for (int i = 0; i < Info.diskNum; i++) {
            // 基础准备
            // 本磁盘的tag从前到后分布
            ArrayList<TagInfo> tagInfos = tagInfo.get(i);
            // 初始化每一个tag在此硬盘中的位置信息

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
                tagReaderLogger.debug("时间片 " + Info.timestamp + " jump");
                continue;
            }
            // 假设现在不在Efficienttag的区域
            disk.isInEfficientTag = false;
            // 初始化每一个tag在此硬盘中的位置信息，同时检查是不是在Efficienttag区域
            for (int temp = 0; temp < tagInfos.size(); temp++) {
                TagInfo taginfo = tagInfos.get(temp);
                // taginfo.left = taginfo.middle - Info.tags.get(taginfo.tagid).sizeList.get(i)
                // / 2;
                // taginfo.end = taginfo.middle + Info.tags.get(taginfo.tagid).sizeList.get(i) /
                // 2;
                if (temp < 1) {
                    taginfo.left = 0;
                    taginfo.end = tagInfos.get(temp + 1).middle - 1;
                } else if (temp > tagInfos.size() - 2) {
                    taginfo.left = tagInfos.get(temp - 1).middle + 1;
                    taginfo.end = Info.MAX_RW_END;
                } else {
                    taginfo.left = tagInfos.get(temp - 1).middle + 1;
                    taginfo.end = tagInfos.get(temp + 1).middle - 1;
                }
                // 检查现在是不是在Efficient区域

                if (IO.periodToTagSet.get(period).contains(taginfo.tagid)) {
                    if (disk.ptr >= taginfo.left && disk.ptr <= taginfo.end) {
                        disk.isInEfficientTag = true;
                    }
                }
            }
            // 如果不在Efficient区域，则进行寻找Efficient区域
            if (!disk.isInEfficientTag) {

                for (int temp = 0; temp < tagInfos.size(); temp++) {
                    // 如果上一个tick已经跳转过了，则不能继续进行jump
                    if (disk.preoper == Info.Action.JUMP)
                        break;

                    TagInfo taginfo = tagInfos.get(temp);
                    // 如果磁盘指针在这个tag区域的左侧，并且这个区域是Efficienttag
                    if (disk.ptr < taginfo.left && IO.periodToTagSet.get(period).contains(taginfo.tagid)) {
                        // 判断是进行跳跃还是进行pass

                        if (taginfo.left - disk.ptr >= tokenNow) {
                            readCommandOut.actions.add(Info.Action.JUMP);
                            readCommandOut.jumpTarget = 0 > taginfo.left ? 0 : taginfo.left;
                            disk.ptrDoAction(Info.Action.JUMP, readCommandOut.jumpTarget);
                            disk.preoper = Info.Action.JUMP;
                            disk.pretoken = Info.tokenPerTick;
                            readCommandOuts.put(i, readCommandOut);
                            tokenNow -= tokenNow;
                            readerLogger.debug("指针跳转" + disk.ptr);
                        } else {
                            // 进行pass，直到和left相等
                            while (disk.ptr < taginfo.left) {
                                processAction(Info.Action.PASS, disk, readCommandOut);
                                tokenNow -= disk.pretoken;
                            }
                            readerLogger.debug("指针跳转" + disk.ptr);
                        }
                        // 进行移动ptr操作后进行退出
                        disk.isInEfficientTag = true;
                        break;
                    }
                }
                // 如果在ptr所在位置向后找不到，就从最前面找
                if (!disk.isInEfficientTag) {
                    for (int temp = 0; temp < tagInfos.size(); temp++) {
                        // 如果上一个tick已经跳转过了，则不能继续进行jump
                        if (disk.preoper == Info.Action.JUMP)
                            break;

                        TagInfo taginfo = tagInfos.get(temp);
                        readerLogger.debug("找到left为" + taginfo.left + "tagid" + taginfo.tagid);
                        // 如果磁盘指针在这个tag区域的左侧，并且这个区域是Efficienttag
                        if (IO.periodToTagSet.get(period).contains(taginfo.tagid)) {
                            // 直接跳跃到寻找到的第一个EfficientTag区域
                            readCommandOut.actions.add(Info.Action.JUMP);
                            readCommandOut.jumpTarget = 0 > taginfo.left ? 0 : taginfo.left;
                            disk.ptrDoAction(Info.Action.JUMP, readCommandOut.jumpTarget);
                            disk.preoper = Info.Action.JUMP;
                            disk.pretoken = Info.tokenPerTick;
                            readCommandOuts.put(i, readCommandOut);
                            tokenNow -= tokenNow;
                            // 进行移动ptr操作后进行退出
                            disk.isInEfficientTag = true;
                            readerLogger.debug("指针跳转" + disk.ptr);
                            break;
                        }
                    }
                }

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
                } else if (k == 2 && tokenNow > calculateToken(Info.Action.PASS, disk)) {
                    if (disk.pretoken < 34 && tokenNow > 116) {
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
                }
                // 任务离得很远
                else {
                    readerLogger.debug("向后寻找不到任务");
                    while (k > 0) {
                        processAction(Info.Action.PASS, disk, readCommandOut);
                        tokenNow -= disk.pretoken;
                        k--;
                    }
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

    public TagReaderStrategy() {

        for (int i = 0; i < Info.diskNum; i++) {
            ArrayList<TagInfo> tagInfos = new ArrayList<>();
            for (int j = 0; j < Info.tagNums; j++) {
                TagInfo taginfo = new TagInfo();
                taginfo.tagid = j;
                taginfo.middle = Info.tags.get(j).middleList.get(i);
                if (taginfo.middle == 0) {
                    continue;
                }
                tagInfos.add(taginfo);
            }
            tagInfos.sort(new middleComparator());
            tagInfo.add(tagInfos);
        }
    }
}
