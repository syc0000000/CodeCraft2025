// script to print ser file
// script to print ser file

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class print_ser {
    private static final Set<Object> visitedObjects = new HashSet<>();
    private static final int MAX_ITEMS = 100;
    private static final int MAX_DEPTH = 10;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法: java print_ser <ser文件路径>");
            System.exit(1);
        }

        String filePath = args[0];
        
        try (FileInputStream fileIn = new FileInputStream(filePath);
             ObjectInputStream objectIn = new ObjectInputStream(fileIn)) {
            
            // 从文件读取对象
            Object obj = objectIn.readObject();
            
            // 打印对象信息
            System.out.println("反序列化对象类型: " + obj.getClass().getName());
            System.out.println("对象内容:");
            
            // 清除已访问对象集合
            visitedObjects.clear();
            printObjectDetails(obj, 0);
            
        } catch (IOException e) {
            System.err.println("读取序列化文件错误: " + e.getMessage());
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("找不到序列化对象的类: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void printObjectDetails(Object obj, int depth) {
        if (depth > MAX_DEPTH) {
            System.out.println("... (达到最大深度)");
            return;
        }
        
        if (obj == null) {
            System.out.println("null");
            return;
        }
        
        String indent = " ".repeat(depth * 2);
        
        // 避免循环引用
        if (visitedObjects.contains(obj)) {
            System.out.println(indent + "<循环引用>");
            return;
        }
        
        // 处理基本类型和字符串
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean || obj instanceof Character) {
            System.out.println(obj);
            return;
        }
        
        // 记录已访问对象以防止无限递归
        if (!obj.getClass().isPrimitive()) {
            visitedObjects.add(obj);
        }
        
        // 处理集合
        if (obj instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) obj;
            System.out.println("集合大小: " + collection.size());
            
            int count = 0;
            for (Object item : collection) {
                if (count >= MAX_ITEMS) {
                    System.out.println(indent + "... (更多项，已截断)");
                    break;
                }
                System.out.print(indent + "[" + count + "] ");
                printObjectDetails(item, depth + 1);
                count++;
            }
            return;
        }
        
        // 处理Map
        if (obj instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) obj;
            System.out.println("Map大小: " + map.size());
            
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count >= MAX_ITEMS) {
                    System.out.println(indent + "... (更多条目，已截断)");
                    break;
                }
                System.out.println(indent + "键: " + entry.getKey());
                System.out.print(indent + "值: ");
                printObjectDetails(entry.getValue(), depth + 1);
                count++;
            }
            return;
        }
        
        // 处理数组
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            System.out.println("数组长度: " + length);
            
            for (int i = 0; i < Math.min(length, MAX_ITEMS); i++) {
                System.out.print(indent + "[" + i + "] ");
                printObjectDetails(Array.get(obj, i), depth + 1);
            }
            
            if (length > MAX_ITEMS) {
                System.out.println(indent + "... (更多项，已截断)");
            }
            return;
        }
        
        // 使用反射打印对象字段值
        System.out.println("对象类型: " + obj.getClass().getName());
        Class<?> clazz = obj.getClass();
        
        // 获取所有字段，包括私有字段
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    System.out.print(indent + field.getName() + " = ");
                    printObjectDetails(field.get(obj), depth + 1);
                } catch (IllegalAccessException e) {
                    System.out.println(indent + field.getName() + " = <无法访问>");
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
}