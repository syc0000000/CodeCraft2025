package IO.hardcode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import IO.model.DiskDistributionGA;

public class dist1 {
        public static Map<Integer, List<DiskDistributionGA.Split>> createHardcodedDistribution() {
                Map<Integer, List<DiskDistributionGA.Split>> distribution = new HashMap<>();

                // Tag 0
                distribution.put(0,
                                Arrays.asList(new DiskDistributionGA.Split(4, 12.04),
                                                new DiskDistributionGA.Split(2, 2.36),
                                                new DiskDistributionGA.Split(9, 6.98),
                                                new DiskDistributionGA.Split(0, 6.43),
                                                new DiskDistributionGA.Split(5, 5.85),
                                                new DiskDistributionGA.Split(8, 14.23),
                                                new DiskDistributionGA.Split(3, 10.91),
                                                new DiskDistributionGA.Split(6, 13.63),
                                                new DiskDistributionGA.Split(7, 14.91),
                                                new DiskDistributionGA.Split(1, 12.67)));

                // Tag 1
                distribution.put(1,
                                Arrays.asList(new DiskDistributionGA.Split(3, 4.31),
                                                new DiskDistributionGA.Split(5, 14.16),
                                                new DiskDistributionGA.Split(1, 14.70),
                                                new DiskDistributionGA.Split(8, 7.49),
                                                new DiskDistributionGA.Split(2, 0.22),
                                                new DiskDistributionGA.Split(4, 11.69),
                                                new DiskDistributionGA.Split(6, 6.65),
                                                new DiskDistributionGA.Split(7, 17.17),
                                                new DiskDistributionGA.Split(9, 19.49),
                                                new DiskDistributionGA.Split(0, 4.11)));

                // Tag 2
                distribution.put(2,
                                Arrays.asList(new DiskDistributionGA.Split(2, 10.20),
                                                new DiskDistributionGA.Split(8, 13.09),
                                                new DiskDistributionGA.Split(4, 9.86),
                                                new DiskDistributionGA.Split(0, 7.50),
                                                new DiskDistributionGA.Split(9, 11.57),
                                                new DiskDistributionGA.Split(5, 12.21),
                                                new DiskDistributionGA.Split(6, 6.27),
                                                new DiskDistributionGA.Split(7, 9.29),
                                                new DiskDistributionGA.Split(3, 20.00)));

                // Tag 3
                distribution.put(3,
                                Arrays.asList(new DiskDistributionGA.Split(6, 3.21),
                                                new DiskDistributionGA.Split(3, 12.87),
                                                new DiskDistributionGA.Split(9, 5.67),
                                                new DiskDistributionGA.Split(4, 4.42),
                                                new DiskDistributionGA.Split(5, 17.89),
                                                new DiskDistributionGA.Split(1, 12.13),
                                                new DiskDistributionGA.Split(0, 0.91),
                                                new DiskDistributionGA.Split(8, 17.53),
                                                new DiskDistributionGA.Split(7, 17.07),
                                                new DiskDistributionGA.Split(2, 8.31)));

                // Tag 4
                distribution.put(4,
                                Arrays.asList(new DiskDistributionGA.Split(0, 7.04),
                                                new DiskDistributionGA.Split(2, 14.23),
                                                new DiskDistributionGA.Split(8, 10.81),
                                                new DiskDistributionGA.Split(1, 9.18),
                                                new DiskDistributionGA.Split(9, 9.52),
                                                new DiskDistributionGA.Split(4, 22.86),
                                                new DiskDistributionGA.Split(7, 0.78),
                                                new DiskDistributionGA.Split(5, 20.72),
                                                new DiskDistributionGA.Split(6, 4.86)));

                // Tag 5
                distribution.put(5,
                                Arrays.asList(new DiskDistributionGA.Split(2, 3.37),
                                                new DiskDistributionGA.Split(9, 11.32),
                                                new DiskDistributionGA.Split(0, 20.35),
                                                new DiskDistributionGA.Split(5, 14.06),
                                                new DiskDistributionGA.Split(6, 10.35),
                                                new DiskDistributionGA.Split(7, 20.55),
                                                new DiskDistributionGA.Split(8, 20.00)));

                // Tags 6-15 follow the same pattern...
                // Adding Tag 6
                distribution.put(6,
                                Arrays.asList(new DiskDistributionGA.Split(4, 10.33),
                                                new DiskDistributionGA.Split(1, 12.09),
                                                new DiskDistributionGA.Split(8, 19.69),
                                                new DiskDistributionGA.Split(5, 2.16),
                                                new DiskDistributionGA.Split(0, 6.12),
                                                new DiskDistributionGA.Split(3, 12.50),
                                                new DiskDistributionGA.Split(6, 6.55),
                                                new DiskDistributionGA.Split(9, 1.67),
                                                new DiskDistributionGA.Split(2, 28.89)));

                // Adding Tag 7
                distribution.put(7,
                                Arrays.asList(new DiskDistributionGA.Split(1, 17.67),
                                                new DiskDistributionGA.Split(8, 2.51),
                                                new DiskDistributionGA.Split(7, 13.89),
                                                new DiskDistributionGA.Split(5, 6.37),
                                                new DiskDistributionGA.Split(2, 11.27),
                                                new DiskDistributionGA.Split(9, 12.81),
                                                new DiskDistributionGA.Split(4, 17.91),
                                                new DiskDistributionGA.Split(0, 10.05),
                                                new DiskDistributionGA.Split(3, 3.84),
                                                new DiskDistributionGA.Split(6, 3.69)));

                // Adding Tag 8
                distribution.put(8,
                                Arrays.asList(new DiskDistributionGA.Split(2, 16.13),
                                                new DiskDistributionGA.Split(3, 19.76),
                                                new DiskDistributionGA.Split(4, 13.84),
                                                new DiskDistributionGA.Split(5, 13.89),
                                                new DiskDistributionGA.Split(9, 16.19),
                                                new DiskDistributionGA.Split(8, 20.19)));

                // Adding Tag 9
                distribution.put(9,
                                Arrays.asList(new DiskDistributionGA.Split(4, 6.36),
                                                new DiskDistributionGA.Split(5, 13.14),
                                                new DiskDistributionGA.Split(8, 10.55),
                                                new DiskDistributionGA.Split(7, 10.20),
                                                new DiskDistributionGA.Split(9, 10.67),
                                                new DiskDistributionGA.Split(2, 6.69),
                                                new DiskDistributionGA.Split(6, 12.86),
                                                new DiskDistributionGA.Split(0, 11.94),
                                                new DiskDistributionGA.Split(3, 17.59)));

                // Adding Tag 10
                distribution.put(10,
                                Arrays.asList(new DiskDistributionGA.Split(1, 9.51),
                                                new DiskDistributionGA.Split(3, 28.92),
                                                new DiskDistributionGA.Split(6, 26.71),
                                                new DiskDistributionGA.Split(7, 23.71),
                                                new DiskDistributionGA.Split(8, 11.14)));

                // Adding Tag 11
                distribution.put(11,
                                Arrays.asList(new DiskDistributionGA.Split(5, 25.74),
                                                new DiskDistributionGA.Split(4, 5.05),
                                                new DiskDistributionGA.Split(6, 10.96),
                                                new DiskDistributionGA.Split(0, 32.17),
                                                new DiskDistributionGA.Split(9, 26.09)));

                // Adding Tag 12
                distribution.put(12,
                                Arrays.asList(new DiskDistributionGA.Split(6, 20.04),
                                                new DiskDistributionGA.Split(9, 0.05),
                                                new DiskDistributionGA.Split(5, 0.25),
                                                new DiskDistributionGA.Split(7, 11.27),
                                                new DiskDistributionGA.Split(8, 0.07),
                                                new DiskDistributionGA.Split(2, 20.63),
                                                new DiskDistributionGA.Split(1, 13.95),
                                                new DiskDistributionGA.Split(4, 16.18),
                                                new DiskDistributionGA.Split(3, 17.56)));

                // Adding Tag 13
                distribution.put(13,
                                Arrays.asList(new DiskDistributionGA.Split(1, 7.57),
                                                new DiskDistributionGA.Split(0, 23.91),
                                                new DiskDistributionGA.Split(9, 12.02),
                                                new DiskDistributionGA.Split(5, 6.01),
                                                new DiskDistributionGA.Split(2, 9.11),
                                                new DiskDistributionGA.Split(7, 16.01),
                                                new DiskDistributionGA.Split(8, 8.05),
                                                new DiskDistributionGA.Split(3, 12.69),
                                                new DiskDistributionGA.Split(4, 4.51),
                                                new DiskDistributionGA.Split(6, 0.11)));

                // Adding Tag 14
                distribution.put(14,
                                Arrays.asList(new DiskDistributionGA.Split(6, 33.26),
                                                new DiskDistributionGA.Split(3, 11.74),
                                                new DiskDistributionGA.Split(2, 8.25),
                                                new DiskDistributionGA.Split(5, 15.25),
                                                new DiskDistributionGA.Split(1, 25.17),
                                                new DiskDistributionGA.Split(8, 6.34)));

                // Adding Tag 15
                distribution.put(15,
                                Arrays.asList(new DiskDistributionGA.Split(4, 14.51),
                                                new DiskDistributionGA.Split(0, 13.48),
                                                new DiskDistributionGA.Split(1, 20.71),
                                                new DiskDistributionGA.Split(6, 16.70),
                                                new DiskDistributionGA.Split(2, 11.80),
                                                new DiskDistributionGA.Split(9, 10.25),
                                                new DiskDistributionGA.Split(7, 12.54)));

                return distribution;
        }
}
