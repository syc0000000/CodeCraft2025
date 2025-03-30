package IO.hardcode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import IO.model.DiskDistributionGA;

public class dist2 {
        public static Map<Integer, List<DiskDistributionGA.Split>> createHardcodedDistribution() {
                Map<Integer, List<DiskDistributionGA.Split>> distribution = new HashMap<>();

                // Tag 0
                distribution.put(0,
                                Arrays.asList(new DiskDistributionGA.Split(2, 10.55),
                                                new DiskDistributionGA.Split(9, 10.55),
                                                new DiskDistributionGA.Split(3, 17.35),
                                                new DiskDistributionGA.Split(4, 10.66),
                                                new DiskDistributionGA.Split(1, 10.01),
                                                new DiskDistributionGA.Split(6, 20.87),
                                                new DiskDistributionGA.Split(7, 20.00)));

                // Tag 1
                distribution.put(1,
                                Arrays.asList(new DiskDistributionGA.Split(8, 15.27),
                                                new DiskDistributionGA.Split(6, 21.47),
                                                new DiskDistributionGA.Split(1, 20.27),
                                                new DiskDistributionGA.Split(3, 22.44),
                                                new DiskDistributionGA.Split(7, 20.55)));

                // Tag 2
                distribution.put(2,
                                Arrays.asList(new DiskDistributionGA.Split(0, 17.93),
                                                new DiskDistributionGA.Split(2, 13.50),
                                                new DiskDistributionGA.Split(3, 13.42),
                                                new DiskDistributionGA.Split(6, 11.02),
                                                new DiskDistributionGA.Split(4, 13.33),
                                                new DiskDistributionGA.Split(9, 19.19),
                                                new DiskDistributionGA.Split(8, 11.60)));

                // Tag 3
                distribution.put(3,
                                Arrays.asList(new DiskDistributionGA.Split(0, 11.82),
                                                new DiskDistributionGA.Split(4, 31.92),
                                                new DiskDistributionGA.Split(7, 34.94),
                                                new DiskDistributionGA.Split(9, 21.32)));

                // Tag 4
                distribution.put(4,
                                Arrays.asList(new DiskDistributionGA.Split(5, 24.99),
                                                new DiskDistributionGA.Split(9, 10.96),
                                                new DiskDistributionGA.Split(1, 22.09),
                                                new DiskDistributionGA.Split(0, 28.09),
                                                new DiskDistributionGA.Split(3, 13.86)));

                // Tag 5
                distribution.put(5,
                                Arrays.asList(new DiskDistributionGA.Split(5, 13.94),
                                                new DiskDistributionGA.Split(6, 12.68),
                                                new DiskDistributionGA.Split(2, 13.19),
                                                new DiskDistributionGA.Split(0, 20.83),
                                                new DiskDistributionGA.Split(4, 17.49),
                                                new DiskDistributionGA.Split(1, 21.86)));

                // Tag 6
                distribution.put(6,
                                Arrays.asList(new DiskDistributionGA.Split(0, 16.69),
                                                new DiskDistributionGA.Split(9, 31.51),
                                                new DiskDistributionGA.Split(7, 10.00),
                                                new DiskDistributionGA.Split(4, 10.00),
                                                new DiskDistributionGA.Split(3, 31.80)));

                // Tag 7
                distribution.put(7,
                                Arrays.asList(new DiskDistributionGA.Split(1, 28.38),
                                                new DiskDistributionGA.Split(7, 20.48),
                                                new DiskDistributionGA.Split(4, 19.66),
                                                new DiskDistributionGA.Split(0, 19.80),
                                                new DiskDistributionGA.Split(5, 11.68)));

                // Tag 8
                distribution.put(8,
                                Arrays.asList(new DiskDistributionGA.Split(2, 28.69),
                                                new DiskDistributionGA.Split(1, 25.28),
                                                new DiskDistributionGA.Split(5, 19.16),
                                                new DiskDistributionGA.Split(3, 26.87)));

                // Tag 9
                distribution.put(9,
                                Arrays.asList(new DiskDistributionGA.Split(6, 31.80),
                                                new DiskDistributionGA.Split(8, 24.59),
                                                new DiskDistributionGA.Split(9, 10.82),
                                                new DiskDistributionGA.Split(4, 32.80)));

                // Tag 10
                distribution.put(10,
                                Arrays.asList(new DiskDistributionGA.Split(2, 27.64),
                                                new DiskDistributionGA.Split(9, 15.99),
                                                new DiskDistributionGA.Split(8, 15.28),
                                                new DiskDistributionGA.Split(5, 13.60),
                                                new DiskDistributionGA.Split(0, 27.50)));

                // Tag 11
                distribution.put(11,
                                Arrays.asList(new DiskDistributionGA.Split(7, 10.98),
                                                new DiskDistributionGA.Split(3, 13.11),
                                                new DiskDistributionGA.Split(6, 16.59),
                                                new DiskDistributionGA.Split(9, 13.80),
                                                new DiskDistributionGA.Split(8, 14.10),
                                                new DiskDistributionGA.Split(2, 11.42),
                                                new DiskDistributionGA.Split(5, 20.00)));

                // Tag 12
                distribution.put(12,
                                Arrays.asList(new DiskDistributionGA.Split(5, 17.57),
                                                new DiskDistributionGA.Split(2, 14.20),
                                                new DiskDistributionGA.Split(6, 22.65),
                                                new DiskDistributionGA.Split(1, 18.06),
                                                new DiskDistributionGA.Split(8, 27.53)));

                // Tag 13
                distribution.put(13,
                                Arrays.asList(new DiskDistributionGA.Split(7, 65.51),
                                                new DiskDistributionGA.Split(8, 24.49),
                                                new DiskDistributionGA.Split(0, 10.00)));

                // Tag 14
                distribution.put(14,
                                Arrays.asList(new DiskDistributionGA.Split(6, 12.97),
                                                new DiskDistributionGA.Split(9, 13.63),
                                                new DiskDistributionGA.Split(2, 17.67),
                                                new DiskDistributionGA.Split(3, 11.74),
                                                new DiskDistributionGA.Split(4, 10.00),
                                                new DiskDistributionGA.Split(5, 13.11),
                                                new DiskDistributionGA.Split(8, 20.89)));

                // Tag 15
                distribution.put(15,
                                Arrays.asList(new DiskDistributionGA.Split(8, 16.93),
                                                new DiskDistributionGA.Split(1, 12.72),
                                                new DiskDistributionGA.Split(2, 28.15),
                                                new DiskDistributionGA.Split(6, 21.19),
                                                new DiskDistributionGA.Split(4, 21.00)));

                return distribution;
        }
}