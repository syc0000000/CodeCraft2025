package IO.Preprocess.hardcode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import IO.Preprocess.TagDistribution.DiskDistributor;

/**
 * 硬编码分布策略
 */
public class dist2 {
        /**
         * 创建硬编码的分布策略
         * 
         * @return 硬编码的分布策略
         */
        public static Map<Integer, List<DiskDistributor.Split>> createHardcodedDistribution() {
                Map<Integer, List<DiskDistributor.Split>> distribution = new HashMap<>();

                // 为每个标签创建一个分布
                for (int tagId = 0; tagId < 100; tagId++) {
                        ArrayList<DiskDistributor.Split> splits = new ArrayList<>();

                        // 根据tagId计算在不同磁盘上的分布
                        // 示例：标签0-33分配到磁盘0，标签34-66分配到磁盘1，标签67-99分配到磁盘2
                        if (tagId < 34) {
                                splits.add(new DiskDistributor.Split(0, 100)); // 100%分配到磁盘0
                        } else if (tagId < 67) {
                                splits.add(new DiskDistributor.Split(1, 100)); // 100%分配到磁盘1
                        } else {
                                splits.add(new DiskDistributor.Split(2, 100)); // 100%分配到磁盘2
                        }

                        distribution.put(tagId, splits);
                }

                return distribution;
        }
}