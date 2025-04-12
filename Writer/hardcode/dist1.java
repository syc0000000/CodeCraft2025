package Writer.hardcode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Writer.TagDistribution.DiskDistributor.Split;

public class dist1 {
    public static Map<Integer, List<Split>> createHardcodedDistribution() {
        Map<Integer, List<Split>> distribution = new HashMap<>();

        // Tag 0
        for (int i = 0; i < 16; i++) {
            distribution.put(i, Arrays.asList(new Split(0, 10),
                    new Split(1, 10),
                    new Split(2, 10),
                    new Split(3, 10),
                    new Split(4, 10),
                    new Split(5, 10),
                    new Split(6, 10),
                    new Split(7, 10),
                    new Split(8, 10),
                    new Split(9, 10)));
        }
        return distribution;
    }
}
